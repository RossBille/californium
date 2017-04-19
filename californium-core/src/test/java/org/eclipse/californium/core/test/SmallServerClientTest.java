/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and re-implementation
 *    Dominique Im Obersteg - parsers and initial implementation
 *    Daniel Pauli - parsers and initial implementation
 *    Kai Hudalla - logging
 *    Achim Kraus (Bosch Software Innovations GmbH) - use CoapNetworkRule for
 *                                                    setup of test-network
 *    Achim Kraus (Bosch Software Innovations GmbH) - destroy server after test
 *                                                    increase waiting time to 2s
 *                                                    (hudson seems to sleep from
 *                                                    time to time :-) )
 ******************************************************************************/
package org.eclipse.californium.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

import org.eclipse.californium.category.Medium;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.core.network.EndpointManager;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.MessageDeliverer;
import org.eclipse.californium.elements.runner.RepeatingTestRunner;
import org.eclipse.californium.rule.CoapNetworkRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * This is a small test that tests the exchange of one request and one response.
 */
@RunWith(RepeatingTestRunner.class)
@Category(Medium.class)
public class SmallServerClientTest {

	public static final Logger LOGGER = Logger.getLogger(SmallServerClientTest.class.getName());

	@ClassRule
	public static CoapNetworkRule network = new CoapNetworkRule(CoapNetworkRule.Mode.DIRECT,
			CoapNetworkRule.Mode.NATIVE);

	private static String SERVER_RESPONSE = "server responds hi";

	private CoapServer server;

	private int serverPort;

	@Before
	public void initLogger() {
		LOGGER.info("Start");
		System.out.println(System.lineSeparator() + "Start " + getClass().getSimpleName());
		EndpointManager.clear();
	}

	@After
	public void after() {
		LOGGER.info("Destroy");
		if (null != server) {
			server.destroy();
		}
		LOGGER.info("End");
		System.out.println("End " + getClass().getSimpleName());
	}

	@Test
	public void testNonconfirmable() throws Exception {
		createSimpleServer();

		// send request
		Request request = new Request(CoAP.Code.POST);
		request.setConfirmable(false);
		request.setDestination(InetAddress.getLoopbackAddress());
		request.setDestinationPort(serverPort);
		request.setPayload("client says hi");
		request.send();
		LOGGER.info("client sent request");

		// receive response and check
		Response response = request.waitForResponse(1000);
		LOGGER.info("client finished wait");
		assertNotNull("Client received no response", response);
		assertEquals(response.getPayloadString(), SERVER_RESPONSE);
	}

	private void createSimpleServer() {
		CoapEndpoint endpoint = new CoapEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
		server = new CoapServer();
		server.addEndpoint(endpoint);
		server.setMessageDeliverer(new MessageDeliverer() {

			@Override
			public void deliverRequest(Exchange exchange) {
				LOGGER.info("server received request");
				exchange.sendAccept();
				try {
					Thread.sleep(500);
				} catch (Exception e) {
				}
				Response response = new Response(ResponseCode.CONTENT);
				response.setConfirmable(false);
				response.setPayload(SERVER_RESPONSE);
				exchange.sendResponse(response);
			}

			@Override
			public void deliverResponse(Exchange exchange, Response response) {
			}
		});
		server.start();
		serverPort = endpoint.getAddress().getPort();
	}
}
