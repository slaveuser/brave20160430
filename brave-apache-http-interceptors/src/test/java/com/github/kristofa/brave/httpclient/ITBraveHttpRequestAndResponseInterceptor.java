package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.test.http.DefaultHttpResponseProvider;
import com.github.kristofa.test.http.HttpRequestImpl;
import com.github.kristofa.test.http.HttpResponseImpl;
import com.github.kristofa.test.http.Method;
import com.github.kristofa.test.http.MockHttpServer;
import com.github.kristofa.test.http.UnsatisfiedExpectationException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ITBraveHttpRequestAndResponseInterceptor {

    private final static int PORT = 8082;
    private final static String CONTEXT = "context";
    private final static String PATH = "/a/b";
    private final static String FULL_PATH = "/" + CONTEXT + PATH;
    private final static String FULL_PATH_WITH_QUERY_PARAMS = "/" + CONTEXT + PATH + "?x=1&y=2";
    private final static String REQUEST = "http://localhost:" + PORT + "/" + CONTEXT + PATH;
    private final static String REQUEST_WITH_QUERY_PARAMS = REQUEST + "?x=1&y=2";
    private static final Long SPAN_ID = 151864l;

    private static final Long TRACE_ID = 8494864l;
    private MockHttpServer mockServer;
    private DefaultHttpResponseProvider responseProvider;
    private ClientTracer clientTracer;
    private SpanId spanId;

    @Before
    public void setup() throws IOException {
        clientTracer = mock(ClientTracer.class);
        spanId = mock(SpanId.class);
        when(spanId.getParentSpanId()).thenReturn(null);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);

        responseProvider = new DefaultHttpResponseProvider(true);
        mockServer = new MockHttpServer(PORT, responseProvider);
        mockServer.start();
    }

    @After
    public void tearDown() throws IOException {
        mockServer.stop();
    }

    @Test
    public void testTracingTrue() throws ClientProtocolException, IOException, UnsatisfiedExpectationException {
        when(clientTracer.startNewSpan(Method.GET.name())).thenReturn(spanId);

        final HttpRequestImpl request = new HttpRequestImpl();
        request.method(Method.GET).path(FULL_PATH)
            .httpMessageHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16))
            .httpMessageHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16))
            .httpMessageHeader(BraveHttpHeaders.Sampled.getName(), "1");
        final HttpResponseImpl response = new HttpResponseImpl(200, null, null);
        responseProvider.set(request, response);

        final CloseableHttpClient httpclient =
            HttpClients.custom().addInterceptorFirst(new BraveHttpRequestInterceptor(new ClientRequestInterceptor(clientTracer), new DefaultSpanNameProvider()))
                .addInterceptorFirst(new BraveHttpResponseInterceptor(new ClientResponseInterceptor(clientTracer))).build();
        try {
            final HttpGet httpGet = new HttpGet(REQUEST);
            final CloseableHttpResponse httpClientResponse = httpclient.execute(httpGet);
            try {
                assertEquals(200, httpClientResponse.getStatusLine().getStatusCode());
            } finally {
                httpClientResponse.close();
            }
            mockServer.verify();

            final InOrder inOrder = inOrder(clientTracer);
            inOrder.verify(clientTracer).startNewSpan(Method.GET.name());
            inOrder.verify(clientTracer).submitBinaryAnnotation("http.uri", FULL_PATH);
            inOrder.verify(clientTracer).setClientSent();
            inOrder.verify(clientTracer).setClientReceived();
            verifyNoMoreInteractions(clientTracer);
        } finally {
            httpclient.close();
        }
    }

    @Test
    public void testTracingTrueHttpNoOk() throws ClientProtocolException, IOException, UnsatisfiedExpectationException {
        when(clientTracer.startNewSpan(Method.GET.name())).thenReturn(spanId);

        final HttpRequestImpl request = new HttpRequestImpl();
        request.method(Method.GET).path(FULL_PATH)
                .httpMessageHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16))
                .httpMessageHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16))
                .httpMessageHeader(BraveHttpHeaders.Sampled.getName(), "1");
        final HttpResponseImpl response = new HttpResponseImpl(400, null, null);
        responseProvider.set(request, response);

        final CloseableHttpClient httpclient =
                HttpClients.custom().addInterceptorFirst(new BraveHttpRequestInterceptor(new ClientRequestInterceptor(clientTracer), new DefaultSpanNameProvider()))
                        .addInterceptorFirst(new BraveHttpResponseInterceptor(new ClientResponseInterceptor(clientTracer))).build();
        try {
            final HttpGet httpGet = new HttpGet(REQUEST);
            final CloseableHttpResponse httpClientResponse = httpclient.execute(httpGet);
            try {
                assertEquals(400, httpClientResponse.getStatusLine().getStatusCode());
            } finally {
                httpClientResponse.close();
            }
            mockServer.verify();

            final InOrder inOrder = inOrder(clientTracer);
            inOrder.verify(clientTracer).startNewSpan(Method.GET.name());
            inOrder.verify(clientTracer).submitBinaryAnnotation("http.uri", FULL_PATH);
            inOrder.verify(clientTracer).setClientSent();
            inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", "400");
            inOrder.verify(clientTracer).setClientReceived();
            verifyNoMoreInteractions(clientTracer);
        } finally {
            httpclient.close();
        }
    }

    @Test
    public void testTracingFalse() throws ClientProtocolException, IOException, UnsatisfiedExpectationException {
        when(clientTracer.startNewSpan(Method.GET.name())).thenReturn(null);

        final HttpRequestImpl request = new HttpRequestImpl();
        request.method(Method.GET).path(FULL_PATH).httpMessageHeader(BraveHttpHeaders.Sampled.getName(), "0");
        final HttpResponseImpl response = new HttpResponseImpl(200, null, null);
        responseProvider.set(request, response);

        final CloseableHttpClient httpclient =
                HttpClients.custom().addInterceptorFirst(new BraveHttpRequestInterceptor(new ClientRequestInterceptor(clientTracer), new DefaultSpanNameProvider()))
                        .addInterceptorFirst(new BraveHttpResponseInterceptor(new ClientResponseInterceptor(clientTracer))).build();
        try {
            final HttpGet httpGet = new HttpGet(REQUEST);
            final CloseableHttpResponse httpClientResponse = httpclient.execute(httpGet);
            try {
                assertEquals(200, httpClientResponse.getStatusLine().getStatusCode());
            } finally {
                httpClientResponse.close();
            }
            mockServer.verify();

            final InOrder inOrder = inOrder(clientTracer);
            inOrder.verify(clientTracer).startNewSpan(Method.GET.name());
            inOrder.verify(clientTracer).setClientReceived();
            verifyNoMoreInteractions(clientTracer);
        } finally {
            httpclient.close();
        }
    }

    @Test
    public void testQueryParams() throws ClientProtocolException, IOException, UnsatisfiedExpectationException {
        when(clientTracer.startNewSpan(Method.GET.name())).thenReturn(spanId);

        final HttpRequestImpl request = new HttpRequestImpl();
        request.method(Method.GET).path(FULL_PATH).queryParameter("x", "1").queryParameter("y", "2")
            .httpMessageHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16))
            .httpMessageHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16))
            .httpMessageHeader(BraveHttpHeaders.Sampled.getName(), "1");
        final HttpResponseImpl response = new HttpResponseImpl(200, null, null);
        responseProvider.set(request, response);

        final CloseableHttpClient httpclient =
                HttpClients.custom().addInterceptorFirst(new BraveHttpRequestInterceptor(new ClientRequestInterceptor(clientTracer), new DefaultSpanNameProvider()))
                        .addInterceptorFirst(new BraveHttpResponseInterceptor(new ClientResponseInterceptor(clientTracer))).build();
        try {
            final HttpGet httpGet = new HttpGet(REQUEST_WITH_QUERY_PARAMS);
            final CloseableHttpResponse httpClientResponse = httpclient.execute(httpGet);
            try {
                assertEquals(200, httpClientResponse.getStatusLine().getStatusCode());
            } finally {
                httpClientResponse.close();
            }
            mockServer.verify();

            final InOrder inOrder = inOrder(clientTracer);
            inOrder.verify(clientTracer).startNewSpan(Method.GET.name());
            inOrder.verify(clientTracer).submitBinaryAnnotation("http.uri", FULL_PATH_WITH_QUERY_PARAMS);
            inOrder.verify(clientTracer).setClientSent();
            inOrder.verify(clientTracer).setClientReceived();
            verifyNoMoreInteractions(clientTracer);
        } finally {
            httpclient.close();
        }
    }


}
