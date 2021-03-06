package io.bootique.jersey.client;

import com.google.inject.Inject;
import com.google.inject.Module;
import io.bootique.BQRuntime;
import io.bootique.jersey.JerseyModule;
import io.bootique.jetty.JettyModule;
import io.bootique.test.junit.BQDaemonTestFactory;
import io.bootique.test.junit.BQTestFactory;
import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class ProviderInjectionIT {

    @ClassRule
    public static BQDaemonTestFactory SERVER_APP_FACTORY = new BQDaemonTestFactory();
    private static BQRuntime SERVER_APP;
    @Rule
    public BQTestFactory CLIENT_FACTORY = new BQTestFactory();
    private BQRuntime clientApp;

    @BeforeClass
    public static void startJetty() {
        Module jersey = (binder) -> JerseyModule.extend(binder).addResource(Resource.class);
        Function<BQRuntime, Boolean> startupCheck = r -> r.getInstance(Server.class).isStarted();

        SERVER_APP = SERVER_APP_FACTORY.app("--server")
                .modules(JettyModule.class, JerseyModule.class)
                .module(jersey)
                .startupCheck(startupCheck)
                .start();
    }

    @AfterClass
    public static void stopJetty() {
        SERVER_APP.shutdown();
    }

    @Before
    public void before() {

        Module module = binder -> {
            JerseyClientModule.extend(binder).addFeature(TestResponseReaderFeature.class);
            binder.bind(InjectedService.class);
        };

        this.clientApp = CLIENT_FACTORY.app().module(JerseyClientModule.class).module(module).createRuntime();
    }

    @Test
    public void testResponse() {

        Client client = clientApp.getInstance(HttpClientFactory.class).newClient();

        WebTarget target = client.target("http://127.0.0.1:8080/");

        Response r1 = target.request().get();
        assertEquals(Status.OK.getStatusCode(), r1.getStatus());
        assertEquals("[bare_string]_1", r1.readEntity(TestResponse.class).toString());
        r1.close();

        Response r2 = target.request().get();
        assertEquals(Status.OK.getStatusCode(), r2.getStatus());
        assertEquals("[bare_string]_2", r2.readEntity(TestResponse.class).toString());
        r2.close();
    }

    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public static class Resource {

        @GET
        public String get() {
            return "bare_string";
        }
    }

    public static class TestResponse {

        private String string;

        public TestResponse(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    public static class InjectedService {

        private AtomicInteger atomicInt = new AtomicInteger();

        public int getNext() {
            return atomicInt.incrementAndGet();
        }
    }

    public static class TestResponseReaderFeature implements Feature {
        @Override
        public boolean configure(FeatureContext context) {
            context.register(TestResponseReader.class);
            return true;
        }
    }

    @Provider
    public static class TestResponseReader implements MessageBodyReader<TestResponse> {

        private InjectedService service;

        @Inject
        public TestResponseReader(InjectedService service) {
            this.service = service;
        }

        @Override
        public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type.equals(TestResponse.class);
        }

        @Override
        public TestResponse readFrom(Class<TestResponse> type, Type genericType, Annotation[] annotations,
                                     MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
                throws IOException, WebApplicationException {

            String responseLine;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(entityStream, "UTF-8"))) {
                responseLine = in.readLine();
            }

            String s = String.format("[%s]_%s", responseLine, service.getNext());
            return new TestResponse(s);
        }
    }

}
