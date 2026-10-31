package ch.unibas.medizin.lastseen;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Records every request that was dispatched to a controller method. */
public class LastSeenInterceptor implements HandlerInterceptor {

    private final EndpointRegistry registry;

    public LastSeenInterceptor(EndpointRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler,
                                Exception ex) {
        if (!(handler instanceof HandlerMethod)) {
            return;
        }
        // the declared pattern, so /person/1 and /person/2 collapse into /person/{id}
        var pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            registry.touch(request.getMethod(), pattern, response.getStatus());
        }
    }
}
