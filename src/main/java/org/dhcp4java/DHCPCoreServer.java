/*
 *	This file is part of dhcp4java, a DHCP API for the Java language.
 *	(c) 2006 Stephan Hadinger
 *
 *	This library is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU Lesser General Public
 *	License as published by the Free Software Foundation; either
 *	version 2.1 of the License, or (at your option) any later version.
 *
 *	This library is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *	Lesser General Public License for more details.
 *
 *	You should have received a copy of the GNU Lesser General Public
 *	License along with this library; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.dhcp4java;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple generic DHCP Server.
 *
 * The DHCP Server provided is based on a multi-thread model. The main thread listens
 * at the socket, then dispatches work to a pool of threads running the servlet.
 *
 * <p>Configuration: the Server reads the following properties in "/DHCPd.properties"
 * at the root of the class path. You can however provide a properties set when
 * contructing the server. Default values are:
 *
 * <blockquote>
 * <tt>serverAddress=127.0.0.1:67</tt> <i>[address:port]</i>
 * <br>
 * <tt>serverThreads=2</tt> <i>[number of concurrent threads for servlets]</i>
 * </blockquote>
 *
 * <p>Note: this class implements <tt>Runnable</tt> allowing it to be run
 * in a dedicated thread.
 *
 * <p>Example:
 *
 * <pre>
 *     public static void main(String[] args) {
 *         try {
 *             DHCPCoreServer server = DHCPCoreServer.initServer(new DHCPStaticServlet(), null);
 *             new Thread(server).start();
 *         } catch (DHCPServerInitException e) {
 *             // die gracefully
 *         }
 *     }
 * </pre>
 *
 * @author Stephan Hadinger
 * @version 1.00
 */
public final class DHCPCoreServer implements Runnable {

    private static final Logger logger = Logger.getLogger(DHCPCoreServer.class.getName().toLowerCase());
    private   static final int    BOUNDED_QUEUE_SIZE = 20;

    /** default MTU for ethernet */
    private static final int    PACKET_SIZE        = 1500;

    /** the servlet it must run */
    private final DHCPServlet        servlet;
    /** working threads pool. */
    private ThreadPoolExecutor threadPool;
    /** Reference of user-provided parameters */
    private final Properties         userProps;
    /** The socket for receiving and sending. */
    private   DatagramSocket     serverSocket;

    /**
     * Constructor
     *
     * <p>Constructor shall not be called directly. New servers are created through
     * <tt>initServer()</tt> factory.
     */
    private DHCPCoreServer(DHCPServlet servlet, Properties userProps) {
        this.servlet = servlet;
        this.userProps = userProps;
    }

    /**
     * Creates and initializes a new DHCP Server.
     *
     * <p>It instanciates the object, then calls <tt>init()</tt> method.
     *
     * @param servlet the <tt>DHCPServlet</tt> instance processing incoming requests,
     * 			must not be <tt>null</tt>.
     * @param userProps specific properties, overriding file and default properties,
     * 			may be <tt>null</tt>.
     * @return the new <tt>DHCPCoreServer</tt> instance (never null).
     * @throws DHCPServerInitException unable to start the server.
     */
    public static DHCPCoreServer initServer(DHCPServlet servlet, Properties userProps) throws DHCPServerInitException {
    	if (servlet == null) {
            throw new IllegalArgumentException("servlet must not be null");
        }
    	DHCPCoreServer server = new DHCPCoreServer(servlet, userProps);
    	server.init();
    	return server;
    }
    /**
     * Initialize the server context from the Properties, and open socket.
     *
     */
    private void init() throws DHCPServerInitException {
        if (serverSocket != null) {
            throw new IllegalStateException("Server already initialized");
        }

        try {
            initSteps();
        } catch (Exception e) {
            this.serverSocket = null;
            logger.log(Level.SEVERE, "Cannot open socket", e);
            throw new DHCPServerInitException("Unable to init server", e);
        }
    }

    private void initSteps() throws IOException {
        // default built-in minimal properties
        // Consolidated parameters of the server.
        Properties properties = new Properties(DEF_PROPS);

        // try to load default configuration file
        InputStream propFileStream = this.getClass().getResourceAsStream("/DHCPd.properties");
        if (propFileStream != null) {
            properties.load(propFileStream);
        } else {
            logger.severe("Could not load /DHCPd.properties");
        }

        // now integrate provided properties
        if (this.userProps != null) {
            properties.putAll(this.userProps);
        }

        // load socket address, this method may be overriden
        // IP address and port for the server
        InetSocketAddress sockAddress = this.getInetSocketAddress(properties);

        // open socket for listening and sending
        serverSocket = new DatagramSocket(null);
        serverSocket.setBroadcast(true);		// allow sending broadcast
        serverSocket.bind(sockAddress);

        // initialize Thread Pool
        int numThreads = Integer.parseInt(properties.getProperty(SERVER_THREADS));
        int maxThreads = Integer.parseInt(properties.getProperty(SERVER_THREADS_MAX));
        int keepaliveThreads = Integer.parseInt(properties.getProperty(SERVER_THREADS_KEEPALIVE));
        threadPool = new ThreadPoolExecutor(numThreads, maxThreads,
                                                 keepaliveThreads, TimeUnit.MILLISECONDS,
                                                 new ArrayBlockingQueue<Runnable>(BOUNDED_QUEUE_SIZE),
                                                 new ServerThreadFactory());
        threadPool.prestartAllCoreThreads();

        // now intialize the servlet
        servlet.setServer(this);
        servlet.init();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    private void dispatch() {
        try {
            DatagramPacket requestDatagram = new DatagramPacket(
                    new byte[PACKET_SIZE], PACKET_SIZE);
            logger.finer("Waiting for packet");

            // receive datagram
            this.serverSocket.receive(requestDatagram);

            if (logger.isLoggable(Level.FINER)) {
                StringBuilder sbuf = new StringBuilder("Received packet from ");

                DHCPPacket.appendHostAddress(sbuf, requestDatagram.getAddress());
                sbuf.append('(')
                    .append(requestDatagram.getPort())
                    .append(')');
                logger.finer(sbuf.toString());
            }

            // send work to thread pool
            DHCPServletDispatcher dispatcher = new DHCPServletDispatcher(this, servlet, requestDatagram);
            threadPool.execute(dispatcher);
        } catch (IOException e) {
	        logger.log(Level.FINE, "IOException", e);
        }
    }
    /**
     * Send back response packet to client.
     *
     * <p>This is a callback method used by servlet dispatchers to send back responses.
     */
    protected void sendResponse(DatagramPacket responseDatagram) {
        if (responseDatagram == null) {
            return; // skipping
        }

        try {
	        // sending back
            serverSocket.send(responseDatagram);
	    } catch (IOException e) {
	        logger.log(Level.SEVERE, "IOException", e);
	    }
    }
    /**
     * Returns the <tt>InetSocketAddress</tt> for the server (client-side).
     *
     * <pre>
     *
     *  serverAddress (default 127.0.0.1)
     *  serverPort (default 67)
     *
     * </pre>
     *
     * <p>
     * This method can be overriden to specify an non default socket behaviour
     *
     * @param props Properties loaded from /DHCPd.properties
     * @return the socket address, null if there was a problem
     */
    private InetSocketAddress getInetSocketAddress(Properties props) {
        if (props == null) {
            throw new IllegalArgumentException("null props not allowed");
        }
        String serverAddress = props.getProperty(SERVER_ADDRESS);
        if (serverAddress == null) {
            throw new IllegalStateException("Cannot load SERVER_ADDRESS property");
        }
        return parseSocketAddress(serverAddress);
    }

    /**
     * Parse a string of the form 'server:port' or '192.168.1.10:67'.
     *
     * @param address string to parse
     * @return InetSocketAddress newly created
     * @throws IllegalArgumentException if unable to parse string
     */
    public static InetSocketAddress parseSocketAddress(String address) {
        if (address == null) {
            throw new IllegalArgumentException("Null address not allowed");
        }
        int index = address.indexOf(':');
        if (index <= 0) {
            throw new IllegalArgumentException("semicolon missing for port number");
        }

        String serverStr = address.substring(0, index);
        String portStr   = address.substring(index + 1, address.length());
        int    port      = Integer.parseInt(portStr);

        return new InetSocketAddress(serverStr, port);
    }

    /**
     * This is the main loop for accepting new request and delegating work to
     * servlets in different threads.
     *
     */
    public void run() {
        if (this.serverSocket == null) {
            throw new IllegalStateException("Listening socket is not open - terminating");
        }
        // do we need to stop the server?
        boolean stopped = false;
        while (!stopped) {
            try {
                dispatch();		// do the stuff
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unexpected Exception", e);
            }
        }
    }

    private static final Properties DEF_PROPS = new Properties();

    public static final String SERVER_ADDRESS = "serverAddress";
    private static final String SERVER_ADDRESS_DEFAULT = "127.0.0.1:67";
    public static final String SERVER_THREADS = "serverThreads";
    private static final String SERVER_THREADS_DEFAULT = "2";
    public static final String SERVER_THREADS_MAX = "serverThreadsMax";
    private static final String SERVER_THREADS_MAX_DEFAULT = "4";
    public static final String SERVER_THREADS_KEEPALIVE = "serverThreadsKeepalive";
    private static final String SERVER_THREADS_KEEPALIVE_DEFAULT = "10000";

    static {
        // initialize defProps
        DEF_PROPS.put(SERVER_ADDRESS, SERVER_ADDRESS_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS, SERVER_THREADS_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS_MAX, SERVER_THREADS_MAX_DEFAULT);
        DEF_PROPS.put(SERVER_THREADS_KEEPALIVE, SERVER_THREADS_KEEPALIVE_DEFAULT);
    }

    private static class ServerThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);

        final AtomicInteger threadNumber = new AtomicInteger(1);
        final String        namePrefix;

        ServerThreadFactory() {
            this.namePrefix = "DHCPCoreServer-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, this.namePrefix + this.threadNumber.getAndIncrement());
        }
    }

}


/**
 * Servlet dispatcher
 */
final class DHCPServletDispatcher implements Runnable {
    private static final Logger logger = Logger.getLogger(DHCPServletDispatcher.class.getName().toLowerCase());

    private final DHCPCoreServer     server;
    private final DHCPServlet    dispatchServlet;
    private final DatagramPacket dispatchPacket;

    public DHCPServletDispatcher(DHCPCoreServer server, DHCPServlet servlet, DatagramPacket req) {
        this.server          = server;
        this.dispatchServlet = servlet;
        this.dispatchPacket  = req;
    }

    public void run() {
        try {
            DatagramPacket response = this.dispatchServlet.serviceDatagram(this.dispatchPacket);
            this.server.sendResponse(response);		// invoke callback method
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception in dispatcher", e);
        }
    }
}
