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

/**
 * General Interface for a "DHCP Servlet"
 *
 * @author Stephan Hadinger
 * @version 1.00
 */
public interface DHCPServlet {

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
    DatagramPacket serviceDatagram(DatagramPacket requestDatagram);

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
    DHCPPacket service(DHCPPacket request);

}
