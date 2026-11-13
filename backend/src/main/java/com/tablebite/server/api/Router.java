package com.tablebite.server.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal path router supporting {param} segments, e.g. /api/orders/{id}. */
public final class Router {

    @FunctionalInterface
    public interface Handler {
        void handle(ApiContext context) throws Exception;
    }

    record Route(String method, Pattern pattern, List<String> names, Handler handler) {
    }

    /** Result of a lookup: the handler (or null) and whether the path exists for another method. */
    record Match(Handler handler, Map<String, String> params, boolean pathExists) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String path, Handler handler) {
        return add("GET", path, handler);
    }

    public Router post(String path, Handler handler) {
        return add("POST", path, handler);
    }

    public Router put(String path, Handler handler) {
        return add("PUT", path, handler);
    }

    public Router patch(String path, Handler handler) {
        return add("PATCH", path, handler);
    }

    public Router delete(String path, Handler handler) {
        return add("DELETE", path, handler);
    }

    private Router add(String method, String path, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([a-zA-Z]+)}").matcher(path);
        StringBuilder regex = new StringBuilder("^");
        int last = 0;
        while (matcher.find()) {
            regex.append(Pattern.quote(path.substring(last, matcher.start())));
            regex.append("([^/]+)");
            names.add(matcher.group(1));
            last = matcher.end();
        }
        regex.append(Pattern.quote(path.substring(last))).append("/?$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
        return this;
    }

    Match match(String method, String path) {
        boolean pathExists = false;
        for (Route route : routes) {
            Matcher matcher = route.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }
            pathExists = true;
            if (route.method().equals(method)) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < route.names().size(); i++) {
                    params.put(route.names().get(i), matcher.group(i + 1));
                }
                return new Match(route.handler(), params, true);
            }
        }
        return new Match(null, Map.of(), pathExists);
    }
}
