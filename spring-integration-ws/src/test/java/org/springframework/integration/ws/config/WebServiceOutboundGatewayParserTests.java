/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ws.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;

import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.endpoint.PollingConsumer;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.mapping.AbstractHeaderMapper;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.ws.MarshallingWebServiceOutboundGateway;
import org.springframework.integration.ws.SimpleWebServiceOutboundGateway;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.FaultMessageResolver;
import org.springframework.ws.client.core.SourceExtractor;
import org.springframework.ws.client.core.WebServiceMessageCallback;
import org.springframework.ws.client.core.WebServiceMessageExtractor;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.WebServiceMessageSender;

/**
 * @author Mark Fisher
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Gary Russell
 * @author Artem Bilan
 */
@RunWith(SpringRunner.class)
public class WebServiceOutboundGatewayParserTests {

	private static volatile int adviceCalled;

	@Autowired
	private ApplicationContext context;

	@Test
	public void simpleGatewayWithReplyChannel() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithReplyChannel", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		Object expected = this.context.getBean("outputChannel");
		assertEquals(expected, accessor.getPropertyValue("outputChannel"));
		Assert.assertEquals(Boolean.FALSE, accessor.getPropertyValue("requiresReply"));

		AbstractHeaderMapper.HeaderMatcher requestHeaderMatcher = TestUtils.getPropertyValue(endpoint,
				"handler.headerMapper.requestHeaderMatcher", AbstractHeaderMapper.HeaderMatcher.class);
		assertTrue(requestHeaderMatcher.matchHeader("testRequest"));
		assertFalse(requestHeaderMatcher.matchHeader("testReply"));

		AbstractHeaderMapper.HeaderMatcher replyHeaderMatcher = TestUtils.getPropertyValue(endpoint,
				"handler.headerMapper.replyHeaderMatcher", AbstractHeaderMapper.HeaderMatcher.class);
		assertFalse(replyHeaderMatcher.matchHeader("testRequest"));
		assertTrue(replyHeaderMatcher.matchHeader("testReply"));

		Long sendTimeout = TestUtils.getPropertyValue(gateway, "messagingTemplate.sendTimeout", Long.class);
		assertEquals(Long.valueOf(777), sendTimeout);

		assertSame(this.context.getBean("webServiceTemplate"),
				TestUtils.getPropertyValue(gateway, "webServiceTemplate"));
	}

	@Test
	public void simpleGatewayWithIgnoreEmptyResponseTrueByDefault() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithReplyChannel", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		assertEquals(Boolean.TRUE, accessor.getPropertyValue("ignoreEmptyResponses"));
		Assert.assertEquals(Boolean.FALSE, accessor.getPropertyValue("requiresReply"));
	}

	@Test
	public void simpleGatewayWithIgnoreEmptyResponses() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithIgnoreEmptyResponsesFalseAndRequiresReplyTrue",
				AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		assertEquals(Boolean.FALSE, accessor.getPropertyValue("ignoreEmptyResponses"));
		assertEquals(Boolean.TRUE, accessor.getPropertyValue("requiresReply"));
		assertEquals(Boolean.FALSE, accessor.getPropertyValue("extractPayload"));
	}

	@Test
	public void simpleGatewayWithDefaultSourceExtractor() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithDefaultSourceExtractor", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		assertEquals("DefaultSourceExtractor", accessor.getPropertyValue("sourceExtractor").getClass().getSimpleName());
	}

	@Test
	public void simpleGatewayWithCustomSourceExtractor() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomSourceExtractor", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		SourceExtractor<?> sourceExtractor = (SourceExtractor<?>) context.getBean("sourceExtractor");
		assertEquals(sourceExtractor, accessor.getPropertyValue("sourceExtractor"));
	}

	@Test
	public void simpleGatewayWithCustomRequestCallback() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomRequestCallback", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		WebServiceMessageCallback callback = (WebServiceMessageCallback) context.getBean("requestCallback");
		assertEquals(callback, accessor.getPropertyValue("requestCallback"));
	}

	@Test
	public void simpleGatewayWithCustomMessageFactory() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomMessageFactory", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		WebServiceMessageFactory factory = (WebServiceMessageFactory) context.getBean("messageFactory");
		assertEquals(factory, accessor.getPropertyValue("messageFactory"));
	}

	@Test
	public void simpleGatewayWithCustomSourceExtractorAndMessageFactory() {
		AbstractEndpoint endpoint = context.getBean("gatewayWithCustomSourceExtractorAndMessageFactory", AbstractEndpoint.class);
		SourceExtractor<?> sourceExtractor = (SourceExtractor<?>) context.getBean("sourceExtractor");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		assertEquals(sourceExtractor, accessor.getPropertyValue("sourceExtractor"));
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		WebServiceMessageFactory factory = (WebServiceMessageFactory) context.getBean("messageFactory");
		assertEquals(factory, accessor.getPropertyValue("messageFactory"));
	}

	@Test
	public void simpleGatewayWithCustomFaultMessageResolver() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomFaultMessageResolver", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		FaultMessageResolver resolver = (FaultMessageResolver) context.getBean("faultMessageResolver");
		assertEquals(resolver, accessor.getPropertyValue("faultMessageResolver"));
	}


	@Test
	public void simpleGatewayWithCustomMessageSender() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomMessageSender", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		WebServiceMessageSender messageSender = (WebServiceMessageSender) context.getBean("messageSender");
		assertEquals(messageSender, ((WebServiceMessageSender[]) accessor.getPropertyValue("messageSenders"))[0]);
	}

	@Test
	public void simpleGatewayWithCustomMessageSenderList() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomMessageSenderList", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		WebServiceMessageSender messageSender = (WebServiceMessageSender) context.getBean("messageSender");
		assertEquals(messageSender, ((WebServiceMessageSender[]) accessor.getPropertyValue("messageSenders"))[0]);
		assertEquals("Wrong number of message senders ",
				2, ((WebServiceMessageSender[]) accessor.getPropertyValue("messageSenders")).length);
	}

	@Test
	public void simpleGatewayWithCustomInterceptor() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomInterceptor", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		ClientInterceptor interceptor = context.getBean("interceptor", ClientInterceptor.class);
		assertEquals(interceptor, ((ClientInterceptor[]) accessor.getPropertyValue("interceptors"))[0]);
	}

	@Test
	public void simpleGatewayWithCustomInterceptorList() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithCustomInterceptorList", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		accessor = new DirectFieldAccessor(accessor.getPropertyValue("webServiceTemplate"));
		ClientInterceptor interceptor = context.getBean("interceptor", ClientInterceptor.class);
		assertEquals(interceptor, ((ClientInterceptor[]) accessor.getPropertyValue("interceptors"))[0]);
		assertEquals("Wrong number of interceptors ",
				2, ((ClientInterceptor[]) accessor.getPropertyValue("interceptors")).length);
	}

	@Test
	public void simpleGatewayWithPoller() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithPoller", AbstractEndpoint.class);
		assertEquals(PollingConsumer.class, endpoint.getClass());
		Object triggerObject = new DirectFieldAccessor(endpoint).getPropertyValue("trigger");
		assertEquals(PeriodicTrigger.class, triggerObject.getClass());
		PeriodicTrigger trigger = (PeriodicTrigger) triggerObject;
		DirectFieldAccessor accessor = new DirectFieldAccessor(trigger);
		assertEquals("PeriodicTrigger had wrong period",
				5000, ((Long) accessor.getPropertyValue("period")).longValue());
	}

	@Test
	public void simpleGatewayWithOrder() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithOrderAndAutoStartupFalse", AbstractEndpoint.class);
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		assertEquals(99, new DirectFieldAccessor(gateway).getPropertyValue("order"));
	}

	@Test
	public void simpleGatewayWithStartupFalse() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithOrderAndAutoStartupFalse", AbstractEndpoint.class);
		assertEquals(Boolean.FALSE, new DirectFieldAccessor(endpoint).getPropertyValue("autoStartup"));
	}

	@Test
	public void marshallingGatewayWithAllInOneMarshaller() {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"marshallingWebServiceOutboundGatewayParserTests.xml", this.getClass());
		AbstractEndpoint endpoint = (AbstractEndpoint) context.getBean("gatewayWithAllInOneMarshaller");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = TestUtils.getPropertyValue(endpoint, "handler");
		Marshaller marshaller = context.getBean("marshallerAndUnmarshaller", Marshaller.class);
		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.marshaller", Marshaller.class));
		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.unmarshaller", Unmarshaller.class));
		context.close();
	}

	@Test
	public void marshallingGatewayWithSeparateMarshallerAndUnmarshaller() {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"marshallingWebServiceOutboundGatewayParserTests.xml", this.getClass());
		AbstractEndpoint endpoint = (AbstractEndpoint) context.getBean("gatewayWithSeparateMarshallerAndUnmarshaller");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = TestUtils.getPropertyValue(endpoint, "handler");
		Marshaller marshaller = context.getBean("marshaller", Marshaller.class);
		Unmarshaller unmarshaller = context.getBean("unmarshaller", Unmarshaller.class);
		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.marshaller", Marshaller.class));
		assertSame(unmarshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.unmarshaller", Unmarshaller.class));
		context.close();
	}

	@Test
	public void marshallingGatewayWithCustomRequestCallback() {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"marshallingWebServiceOutboundGatewayParserTests.xml", this.getClass());
		AbstractEndpoint endpoint = (AbstractEndpoint) context.getBean("gatewayWithCustomRequestCallback");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = TestUtils.getPropertyValue(endpoint, "handler");
		assertEquals(MarshallingWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		WebServiceMessageCallback callback = (WebServiceMessageCallback) context.getBean("requestCallback");
		assertEquals(callback, accessor.getPropertyValue("requestCallback"));
		context.close();
	}

	@Test
	public void marshallingGatewayWithAllInOneMarshallerAndMessageFactory() {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"marshallingWebServiceOutboundGatewayParserTests.xml", this.getClass());
		AbstractEndpoint endpoint = (AbstractEndpoint) context.getBean("gatewayWithAllInOneMarshallerAndMessageFactory");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = TestUtils.getPropertyValue(endpoint, "handler");
		Marshaller marshaller = context.getBean("marshallerAndUnmarshaller", Marshaller.class);
		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.marshaller", Marshaller.class));
		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.unmarshaller", Unmarshaller.class));

		WebServiceMessageFactory messageFactory = (WebServiceMessageFactory) context.getBean("messageFactory");
		assertEquals(messageFactory, TestUtils.getPropertyValue(gateway, "webServiceTemplate.messageFactory"));
		context.close();
	}

	@Test
	public void marshallingGatewayWithSeparateMarshallerAndUnmarshallerAndMessageFactory() {
		ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(
				"marshallingWebServiceOutboundGatewayParserTests.xml", this.getClass());
		AbstractEndpoint endpoint = (AbstractEndpoint) context.getBean("gatewayWithSeparateMarshallerAndUnmarshallerAndMessageFactory");
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());

		Object gateway = TestUtils.getPropertyValue(endpoint, "handler");

		Marshaller marshaller = context.getBean("marshaller", Marshaller.class);
		Unmarshaller unmarshaller = context.getBean("unmarshaller", Unmarshaller.class);

		assertSame(marshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.marshaller", Marshaller.class));
		assertSame(unmarshaller, TestUtils.getPropertyValue(gateway, "webServiceTemplate.unmarshaller", Unmarshaller.class));

		WebServiceMessageFactory messageFactory = context.getBean("messageFactory", WebServiceMessageFactory.class);
		assertEquals(messageFactory, TestUtils.getPropertyValue(gateway, "webServiceTemplate.messageFactory"));
		context.close();
	}

	@Test
	public void simpleGatewayWithDestinationProvider() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithDestinationProvider", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		Object gateway = new DirectFieldAccessor(endpoint).getPropertyValue("handler");
		StubDestinationProvider stubProvider = (StubDestinationProvider) context.getBean("destinationProvider");
		assertEquals(SimpleWebServiceOutboundGateway.class, gateway.getClass());
		DirectFieldAccessor accessor = new DirectFieldAccessor(gateway);
		assertEquals("Wrong DestinationProvider", stubProvider, accessor.getPropertyValue("destinationProvider"));
		assertNull(accessor.getPropertyValue("uri"));
		Object destinationProviderObject = new DirectFieldAccessor(
				accessor.getPropertyValue("webServiceTemplate")).getPropertyValue("destinationProvider");
		assertEquals("Wrong DestinationProvider", stubProvider, destinationProviderObject);
	}

	@Test
	public void advised() {
		adviceCalled = 0;
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithAdvice", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		MessageHandler handler = TestUtils.getPropertyValue(endpoint, "handler", MessageHandler.class);
		handler.handleMessage(new GenericMessage<String>("foo"));
		assertEquals(1, adviceCalled);
	}

	@Test
	public void testInt2718AdvisedInsideAChain() {
		adviceCalled = 0;
		MessageChannel channel = context.getBean("gatewayWithAdviceInsideAChain", MessageChannel.class);
		channel.send(new GenericMessage<String>("foo"));
		assertEquals(1, adviceCalled);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void jmsUri() {
		AbstractEndpoint endpoint = this.context.getBean("gatewayWithJmsUri", AbstractEndpoint.class);
		assertEquals(EventDrivenConsumer.class, endpoint.getClass());
		MessageHandler handler = TestUtils.getPropertyValue(endpoint, "handler", MessageHandler.class);
		assertNull(TestUtils.getPropertyValue(handler, "destinationProvider"));
		assertFalse(TestUtils.getPropertyValue(handler, "encodeUri", Boolean.class));

		WebServiceTemplate webServiceTemplate = TestUtils.getPropertyValue(handler, "webServiceTemplate",
				WebServiceTemplate.class);
		webServiceTemplate = spy(webServiceTemplate);

		doReturn(null).when(webServiceTemplate).sendAndReceive(anyString(),
				any(WebServiceMessageCallback.class),
				ArgumentMatchers.<WebServiceMessageExtractor<Object>>any());

		new DirectFieldAccessor(handler).setPropertyValue("webServiceTemplate", webServiceTemplate);

		handler.handleMessage(new GenericMessage<String>("foo"));

		verify(webServiceTemplate).sendAndReceive(eq("jms:wsQueue"),
				any(WebServiceMessageCallback.class),
				ArgumentMatchers.<WebServiceMessageExtractor<Object>>any());
	}

	@Test(expected = BeanDefinitionParsingException.class)
	public void invalidGatewayWithBothUriAndDestinationProvider() {
		new ClassPathXmlApplicationContext("invalidGatewayWithBothUriAndDestinationProvider.xml", this.getClass())
				.close();
	}

	@Test(expected = BeanDefinitionParsingException.class)
	public void invalidGatewayWithNeitherUriNorDestinationProvider() {
		new ClassPathXmlApplicationContext("invalidGatewayWithNeitherUriNorDestinationProvider.xml", this.getClass())
				.close();
	}

	public static class FooAdvice extends AbstractRequestHandlerAdvice {

		@Override
		protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) throws Exception {
			adviceCalled++;
			return null;
		}

	}

}
