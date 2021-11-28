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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple generic DHCP Server.
 *
 * The DHCP Server provided is based on a multi-thread model. The main thread listens
 * at the socket, then dispatches work to a pool of threads running the servlet.

 * @author Stephan Hadinger
 * @version 1.00
 */
public final class DHCPCoreServer implements Runnable {


    /** the servlet it must run */
    private final DHCPServlet        servlet;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);
    private final DatagramSocket     serverSocket;

    /** default MTU for ethernet */
    private static final int    PACKET_SIZE        = 1500;
    private static final Logger logger = Logger.getLogger(DHCPCoreServer.class.getName().toLowerCase());

    private DHCPCoreServer(DHCPServlet servlet, DatagramSocket serverSocket) {
        this.servlet = servlet;
        this.serverSocket = serverSocket;
    }

    /**
     * Creates and initializes a new DHCP Server.
     */
    public static DHCPCoreServer initServer(DHCPServlet servlet) throws IOException {
    	return new DHCPCoreServer(servlet,initSteps());
    }

    private static DatagramSocket initSteps() throws IOException {

        InetSocketAddress sockAddress = new InetSocketAddress("127.0.0.1", 67);

        // open socket for listening and sending
        DatagramSocket serverSocket = new DatagramSocket(null);
        serverSocket.setBroadcast(true);		// allow sending broadcast
        serverSocket.bind(sockAddress);
        return serverSocket;
    }

    /**
     * Send back response packet to client.
     */
    void sendResponse(DatagramPacket responseDatagram) {
        try {
            if (responseDatagram == null) {
                return;
            }
            serverSocket.send(responseDatagram);
	    } catch (IOException e) {
	        logger.log(Level.SEVERE, "IOException", e);
	    }
    }

    /**
     * This is the main loop for accepting new request and delegating work to
     * servlets in different threads.
     */
    public void run() {
        while (true) {
            try {
                threadPool.execute(dispatcher());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Unexpected Exception", e);
            }
        }
    }

    private DHCPServletDispatcher dispatcher() throws IOException {
        DatagramPacket requestDatagram = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);

        // receive datagram
        serverSocket.receive(requestDatagram);

        // send work to thread pool
        return new DHCPServletDispatcher(this, servlet, requestDatagram);
    }

}


