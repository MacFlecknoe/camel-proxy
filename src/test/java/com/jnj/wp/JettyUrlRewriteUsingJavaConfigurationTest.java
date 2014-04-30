package com.jnj.wp;

import static org.junit.Assert.*;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.urlrewrite.http4.Http4UrlRewrite;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Proof of concept to demonstrate use of urlrewrite functionality within camel. The urlrewrite component is an abstraction of 
 * UrlRewriteFilter which is based in turn upon mod_rewrite for the Apache web server.
 * 
 * @see <a href="https://code.google.com/p/urlrewritefilter/">UrlRewriteFilter</a>
 * @see <a href="http://camel.apache.org/urlrewrite.html">UrlRewrite Component</a>
 * @see <a href="http://camel.apache.org/testing.html">Testing Using SingleRouteCamelConfiguration</a>
 *
 */
@ContextConfiguration(
		locations = "com.jnj.wp.JettyUrlRewriteUsingJavaConfigurationTest$TestCamelConfiguration", 
		loader = org.apache.camel.spring.javaconfig.test.JavaConfigContextLoader.class)
public class JettyUrlRewriteUsingJavaConfigurationTest extends AbstractJUnit4SpringContextTests {
	
	@EndpointInject(uri = "mock:result")
	protected MockEndpoint resultEndpoint;
	 
	@Produce(uri = "direct:start")
	protected ProducerTemplate template;
	
	@DirtiesContext
	@Test
	public void testHttp4UrlRewrite() throws Exception {
		String out = template.requestBody("http://localhost:40001/proxy/start/products/100d", null, String.class);
		
		// if the rewrite is successful the product_id variable will have been placed in the message body.
		assertEquals(out, "100d");
		
		resultEndpoint.assertIsSatisfied();
	}
	
	@Configuration
	public static class TestCamelConfiguration extends SingleRouteCamelConfiguration {

		@Bean
		public Http4UrlRewrite myRewrite() {
			
			Http4UrlRewrite myRewrite = new Http4UrlRewrite();
			myRewrite.setConfigFile("rewriteconf.xml");
			
			return myRewrite;
		}
		
		@Bean
		@Override
		public RouteBuilder route() {
			
			return new RouteBuilder() {
				
						public void configure() {
							
							// execute url rewrite (this is our proxy route)
							from("jetty://http://localhost:40001/proxy?matchOnUriPrefix=true").to("http4://localhost:40000?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite");
							
							// if url rewrite is successful this route will be executed and the product_id variable placed in the message for easy retrieval (this route represents our destination server)
							from("jetty:http://localhost:40000/end/products/index.jsp").transform(header("product_id")).to("mock:result");
						}
					};
		}
	}
}
