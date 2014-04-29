package com.jnj.wp;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.component.urlrewrite.http4.Http4UrlRewrite;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Proof of concept to demonstrate use of urlrewrite functionality within camel. The urlrewrite component is an abstraction of 
 * UrlRewriteFilter which is based in turn upon mod_rewrite for the Apache web server.
 * 
 * @see <a href="https://code.google.com/p/urlrewritefilter/">UrlRewriteFilter</a>
 * @see <a href="http://camel.apache.org/urlrewrite.html">UrlRewrite Component</a>
 *
 */
public class JettyUrlRewriteTest extends CamelTestSupport {

	private static volatile int port;
	private static volatile int port2;
	private final AtomicInteger counter = new AtomicInteger(1);

	@BeforeClass
	public static void initPort() throws Exception {
		
		// start from somewhere in the 23xxx range
		port = AvailablePortFinder.getNextAvailable(23000);
		
		// find another ports for proxy route test
		port2 = AvailablePortFinder.getNextAvailable(24000);
	}

	@Override
	protected CamelContext createCamelContext() throws Exception {
		
		CamelContext context = super.createCamelContext();
		context.addComponent("properties", new PropertiesComponent("ref:prop"));
		
		return context;
	}

	@Override
	protected JndiRegistry createRegistry() throws Exception {

		JndiRegistry jndi = super.createRegistry();
		//
		// place ports in registry as properties to be retrieved via camel variable replacement within routes
		//
		Properties prop = new Properties();
		prop.setProperty("port", String.valueOf(getPort()));
		prop.setProperty("port2", String.valueOf(getPort2()));

		jndi.bind("prop", prop);
		//
		// place url rewrite object in the registry for retrieval by camel. this is what we will be testing!
		//
		Http4UrlRewrite myRewrite = new Http4UrlRewrite();
		myRewrite.setConfigFile("rewriteconf.xml");
		
		jndi.bind("myRewrite", myRewrite); 

		return jndi;
	}

	protected int getNextPort() {
		return AvailablePortFinder.getNextAvailable(port + counter.getAndIncrement());
	}

	protected int getNextPort(int startWithPort) {
		return AvailablePortFinder.getNextAvailable(startWithPort);
	}

	protected static int getPort() {
		return port;
	}

	protected static int getPort2() {
		return port2;
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {

		return new RouteBuilder() {
			public void configure() {
				
				// execute url rewrite (this is our proxy route)
				from("jetty://http://localhost:{{port}}/proxy?matchOnUriPrefix=true").to("http4://localhost:{{port2}}?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite");
				
				// if url rewrite is successful this route will be executed and the product_id variable placed in the message for easy retrieval (this route represents our destination server)
				from("jetty:http://localhost:{{port2}}/end/products/index.jsp").transform(header("product_id"));
			}
		};
	}
	
	@Test
	public void testHttp4UrlRewrite() throws Exception {
		String out = template.requestBody("http://localhost:{{port}}/proxy/start/products/100", null, String.class);
		assertEquals(out, "100");
	}
	
}
