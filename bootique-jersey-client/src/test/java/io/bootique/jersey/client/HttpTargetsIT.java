package io.bootique.jersey.client;

import io.bootique.jersey.JerseyModule;
import io.bootique.jetty.JettyModule;
import io.bootique.logback.LogbackModuleProvider;
import io.bootique.test.junit.BQTestFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HttpTargetsIT {

    @ClassRule
    public static BQTestFactory SERVER_FACTORY = new BQTestFactory();

    @Rule
    public BQTestFactory clientFactory = new BQTestFactory();

    @BeforeClass
    public static void beforeClass() {
        SERVER_FACTORY.app("--server")
                .modules(JettyModule.class, JerseyModule.class)
                .module(new LogbackModuleProvider())
                .module(b -> JerseyModule.extend(b).addResource(Resource.class))
                .run();
    }

    @Test
    public void testNewTarget() {
        HttpTargets targets =
                clientFactory.app()
                        .module(new JerseyClientModuleProvider())
                        .module(new LogbackModuleProvider())
                        .property("bq.jerseyclient.targets.t1.url", "http://127.0.0.1:8080/get")
                        .createRuntime()
                        .getInstance(HttpTargets.class);

        WebTarget t1 = targets.newTarget("t1");

        Response r1 = t1.request().get();
        assertEquals(Response.Status.OK.getStatusCode(), r1.getStatus());
        assertEquals("got", r1.readEntity(String.class));

        Response r2 = t1.path("me").request().get();
        assertEquals(Response.Status.OK.getStatusCode(), r2.getStatus());
        assertEquals("got/me", r2.readEntity(String.class));
    }

    @Test
    public void testNewTarget_Auth() {
        HttpTargets targets =
                clientFactory.app()
                        .module(new JerseyClientModuleProvider())
                        .module(new LogbackModuleProvider())
                        .property("bq.jerseyclient.auth.a1.type", "basic")
                        .property("bq.jerseyclient.auth.a1.username", "u")
                        .property("bq.jerseyclient.auth.a1.password", "p")
                        .property("bq.jerseyclient.targets.t1.url", "http://127.0.0.1:8080/get_auth")
                        .property("bq.jerseyclient.targets.t1.auth", "a1")
                        .createRuntime()
                        .getInstance(HttpTargets.class);

        WebTarget t1 = targets.newTarget("t1");

        Response r1 = t1.request().get();
        assertEquals(Response.Status.OK.getStatusCode(), r1.getStatus());
        assertEquals("got_Basic dTpw", r1.readEntity(String.class));
    }

    @Test
    public void testNewTarget_Redirects_Default_Follow() {
        new FollowRedirectsTester(null, null).expectFollow();
    }

    @Test
    public void testNewTarget_Redirects_ClientNoFollow_TargetDefault() {
        new FollowRedirectsTester(false, null).expectNoFollow();
    }

    @Test
    public void testNewTarget_Redirects_ClientNoFollow_TargetFollow() {
        new FollowRedirectsTester(false, true).expectFollow();
    }

    @Test
    public void testNewTarget_Compression_Default_Compressed() {
        new CompressionTester(null, null).expectCompressed();
    }

    @Test
    public void testNewTarget_Compression_ClientNotCompressed_TargetDefault() {
        new CompressionTester(false, null).expectUncompressed();
    }

    @Test
    public void testNewTarget_Compression_ClientNotCompressed_TargetCompressed() {
        new CompressionTester(false, true).expectCompressed();
    }

    @Test
    public void testNewTarget_Compression_ClientCompressed_TargetCompressed() {
        new CompressionTester(true, true).expectCompressed();
    }

    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public static class Resource {

        @GET
        @Path("get")
        public String get() {
            return "got";
        }

        @GET
        @Path("get/me")
        public String getMe() {
            return "got/me";
        }

        @GET
        @Path("get_auth")
        public String getAuth(@HeaderParam("Authorization") String auth) {
            return "got_" + auth;
        }

        @GET
        @Path("302")
        public Response threeOhTwo() throws URISyntaxException {
            return Response.temporaryRedirect(new URI("/get")).build();
        }


        @GET
        @Path("getbig")
        // value big enough to ensure compression kicks in
        public String getBig() {
            return "gotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgotgot";
        }
    }

    class FollowRedirectsTester {
        private Boolean clientRedirects;
        private Boolean targetRedirects;

        public FollowRedirectsTester(Boolean clientRedirects, Boolean targetRedirects) {
            this.clientRedirects = clientRedirects;
            this.targetRedirects = targetRedirects;
        }

        public void expectFollow() {
            Response r = createTarget().request().get();
            assertEquals(200, r.getStatus());
            assertEquals("got", r.readEntity(String.class));
        }

        public void expectNoFollow() {
            Response r = createTarget().request().get();
            assertEquals(307, r.getStatus());
            assertEquals("http://127.0.0.1:8080/get", r.getHeaderString("location"));
        }

        private WebTarget createTarget() {

            BQTestFactory.Builder builder =
                    clientFactory.app()
                            .module(new JerseyClientModuleProvider())
                            .module(new LogbackModuleProvider())
                            .property("bq.jerseyclient.targets.t.url", "http://127.0.0.1:8080/302");

            if (clientRedirects != null) {
                builder.property("bq.jerseyclient.followRedirects", clientRedirects.toString());
            }

            if (targetRedirects != null) {
                builder.property("bq.jerseyclient.targets.t.followRedirects", targetRedirects.toString());
            }

            return builder.createRuntime().getInstance(HttpTargets.class).newTarget("t");
        }
    }

    class CompressionTester {
        private Boolean clientCompression;
        private Boolean targetCompression;

        public CompressionTester(Boolean clientCompression, Boolean targetCompression) {
            this.clientCompression = clientCompression;
            this.targetCompression = targetCompression;
        }

        public void expectCompressed() {
            Response r = createTarget().request().get();
            assertEquals(200, r.getStatus());
            assertEquals("gzip", r.getHeaderString("Content-Encoding"));
        }

        public void expectUncompressed() {
            Response r = createTarget().request().get();
            assertEquals(200, r.getStatus());
            assertNull(r.getHeaderString("Content-Encoding"));
        }

        private WebTarget createTarget() {

            BQTestFactory.Builder builder =
                    clientFactory.app()
                            .module(new JerseyClientModuleProvider())
                            .module(new LogbackModuleProvider())
                            .property("bq.jerseyclient.targets.t.url", "http://127.0.0.1:8080/getbig");

            if (clientCompression != null) {
                builder.property("bq.jerseyclient.compression", clientCompression.toString());
            }

            if (targetCompression != null) {
                builder.property("bq.jerseyclient.targets.t.compression", targetCompression.toString());
            }

            return builder.createRuntime().getInstance(HttpTargets.class).newTarget("t");
        }
    }
}
