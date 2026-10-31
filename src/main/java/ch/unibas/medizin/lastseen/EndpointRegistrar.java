package ch.unibas.medizin.lastseen;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.http.server.PathContainer;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.Set;

/** Lists all request mappings once the application is up. */
public class EndpointRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final EndpointRegistry registry;
    private final ApplicationContext context;
    private final List<PathPattern> excludes;

    public EndpointRegistrar(EndpointRegistry registry, ApplicationContext context, List<String> excludePatterns) {
        this.registry = registry;
        this.context = context;
        this.excludes = excludePatterns.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() != context) {
            return;
        }
        context.getBeansOfType(RequestMappingHandlerMapping.class).values().forEach(mapping ->
                mapping.getHandlerMethods().forEach(this::register));
    }

    private void register(RequestMappingInfo info, HandlerMethod handler) {
        var patterns = info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues()
                : Set.<String>of();
        var methods = info.getMethodsCondition().getMethods();
        // user class, so CGLIB proxies don't leak into the name
        var name = ClassUtils.getUserClass(handler.getBeanType()).getName() + '#' + handler.getMethod().getName();
        for (var pattern : patterns) {
            if (excluded(pattern)) {
                continue;
            }
            if (methods.isEmpty()) {
                registry.register("ANY", pattern, name);
            }
            for (var m : methods) {
                registry.register(m.name(), pattern, name);
            }
        }
    }

    private boolean excluded(String pattern) {
        var path = PathContainer.parsePath(pattern);
        return excludes.stream().anyMatch(e -> e.matches(path));
    }
}
