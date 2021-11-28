package org.dhcp4java;

import java.net.DatagramPacket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet dispatcher
 */
final class DHCPServletDispatcher implements Runnable {

    private final DHCPCoreServer server;
    private final DHCPServlet dispatchServlet;
    private final DatagramPacket dispatchPacket;
    private static final Logger logger = Logger.getLogger(DHCPServletDispatcher.class.getName().toLowerCase());

    public DHCPServletDispatcher(DHCPCoreServer server, DHCPServlet servlet, DatagramPacket req) {
        this.server = server;
        this.dispatchServlet = servlet;
        this.dispatchPacket = req;
    }

    public void run() {
        try {
            DatagramPacket response = this.dispatchServlet.serviceDatagram(this.dispatchPacket);
            this.server.sendResponse(response);
        } catch (Exception e) {
            logger.log(Level.FINE, "Exception in dispatcher", e);
        }
    }
}
