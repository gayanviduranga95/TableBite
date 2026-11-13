package com.tablebite.server.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterAndUploadTest {

    @Test
    void matchesPathParametersAndMethods() {
        Router router = new Router();
        Router.Handler handler = context -> { };
        router.get("/api/orders/{id}", handler).post("/api/tables/lookup", handler).get("/api/tables/{token}", handler);

        Router.Match match = router.match("GET", "/api/orders/42");
        assertNotNull(match.handler());
        assertEquals("42", match.params().get("id"));

        Router.Match wrongMethod = router.match("DELETE", "/api/orders/42");
        assertNull(wrongMethod.handler());
        assertTrue(wrongMethod.pathExists(), "405 rather than 404");

        assertNotNull(router.match("POST", "/api/tables/lookup").handler());
        assertEquals("tb-abc", router.match("GET", "/api/tables/tb-abc").params().get("token"));
        assertFalse(router.match("GET", "/api/unknown").pathExists());
        assertNull(router.match("GET", "/api/orders/1/extra").handler());
    }

    @Test
    void detectsImagesByMagicBytes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        byte[] html = "<html><script>".getBytes();
        assertEquals("jpg", UploadStore.detectExtension(jpeg));
        assertEquals("png", UploadStore.detectExtension(png));
        assertEquals("webp", UploadStore.detectExtension(webp));
        assertNull(UploadStore.detectExtension(html));
        assertNull(UploadStore.detectExtension(new byte[3]));
    }
}
