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

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.dhcp4java.DHCPConstants.*;

/**
 * Simple implementation of DHCPServlet.
 */
public final class SimpleDHCPServlet implements DHCPServlet {

    private static final Logger logger = Logger.getLogger(SimpleDHCPServlet.class.getName().toLowerCase());

    /**
     * Low-level method for receiving a UDP Daragram and sending one back.
     * 
     * <p>This methode normally does not need to be overriden and passes control
     * to <tt>service()</tt> for DHCP packets handling. Howerever the <tt>service()</tt>
     * method is not called if the DHCP request is invalid (i.e. could not be parsed).
     * So overriding this method gives you control on every datagram received, not
     * only valid DHCP packets.
     * 
     * @param requestDatagram the datagram received from the client
     * @return response the datagram to send back, or <tt>null</tt> if no answer
     */
    public DatagramPacket serviceDatagram(DatagramPacket requestDatagram) {

        if (requestDatagram == null) { return null; }

        try {
            return getDatagramPacket(requestDatagram);
        } catch (DHCPBadPacketException e) {
            logger.log(Level.INFO, "Invalid DHCP packet received", e);
        } catch (Exception e) {
            logger.log(Level.INFO, "Unexpected Exception", e);
        }

        // general fallback, we do nothing
        return null;
    }

    private DatagramPacket getDatagramPacket(DatagramPacket requestDatagram) {
        DatagramPacket responseDatagram;
        // parse DHCP request
        DHCPPacket request = DHCPPacket.getPacket(requestDatagram);

        if (request == null) {
            return null;
        }	// nothing much we can do

        if (logger.isLoggable(Level.FINER)) {
            logger.finer(request.toString());
        }

        // do the real work
        DHCPPacket response = this.service(request); // call service function
        // done
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("service() done");
        }
        if (response == null) {
            return null;
        }

        // check address/port
        InetAddress address = response.getAddress();
        if (address == null) {
            logger.warning("Address needed in response");
            return null;
        }
        int port = response.getPort();

        // we have something to send back
        byte[] responseBuf = response.serialize();

        if (logger.isLoggable(Level.FINER)) { logger.finer("Buffer is " + responseBuf.length + " bytes long"); }

        responseDatagram = new DatagramPacket(responseBuf, responseBuf.length, address, port);
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Sending back to" + address.getHostAddress() + '(' + port + ')');
        }
        return responseDatagram;
    }

    /**
     * General method for parsing a DHCP request.
     * 
     * <p>Returns the DHCPPacket to send back to the client, or null if we
     * silently ignore the request.
     * 
     * <p>Default behaviour: ignore BOOTP packets, and dispatch to <tt>doXXX()</tt> methods.
     * 
     * @param request DHCP request from the client
     * @return response DHCP response to send back to client, <tt>null</tt> if no response
     */
    public DHCPPacket service(DHCPPacket request) {
        Byte dhcpMessageType;

        if (request == null) {
        	return null;
        }

        if (!request.isDhcp()) {
            logger.info("BOOTP packet rejected");
            return null;		// skipping old BOOTP
        }

        dhcpMessageType = request.getDHCPMessageType();

        if (dhcpMessageType == null) {
            logger.info("no DHCP message type");
            return null;
        }

        if (request.getOp() == BOOTREQUEST) {
            switch (dhcpMessageType) {
            	case DHCPDISCOVER: return discover(request);
            	case DHCPREQUEST:  return request(request);
            	case DHCPINFORM:   return inform(request);
            	case DHCPDECLINE:  return decline(request);
            	case DHCPRELEASE:  return release(request);

            	default:
            	    logger.info("Unsupported message type " + dhcpMessageType);
            	    return null;
            }
        } else if (request.getOp() == BOOTREPLY) {
            // receiving a BOOTREPLY from a client is not normal
            logger.info("BOOTREPLY received from client");
            return null;
        } else {
            logger.warning("Unknown Op: " + request.getOp());
            return null;	// ignore
        }
    }

    DHCPPacket discover(DHCPPacket request) { return null; }
    DHCPPacket request(DHCPPacket request) { return null; }
    DHCPPacket inform(DHCPPacket request) { return null; }
    DHCPPacket decline(DHCPPacket request) { return null; }
    DHCPPacket release(DHCPPacket request) { return null;}

    public static void main(String[] args) throws Exception {
        DHCPCoreServer.initServer(new SimpleDHCPServlet()).run();
    }
}
