package com.springlaunch.config;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the single-page frontend when one has been built into the jar.
 *
 * <p>The browser owns routes like {@code /projects} and {@code /billing}, but on a hard refresh it
 * asks the server for them and the server has no such endpoints. The fix is to answer those paths
 * with the SPA shell so the client-side router can take over.
 *
 * <p>This resolves inside Spring's resource chain rather than registering a competing controller
 * mapping. That matters: the static resource handler is already mapped at {@code /**}, so a
 * controller trying to claim the same paths fights it for precedence, while a resolver simply
 * decides what a path means once the request has arrived here.
 *
 * <p>Conditional on the built asset being present, so the default backend-only build wires none of
 * this and keeps answering 404 for unknown paths.
 */
@Configuration
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaForwardingConfig implements WebMvcConfigurer {

    private static final String STATIC_ROOT = "classpath:/static/";
    private static final Resource SHELL = new ClassPathResource("static/index.html");

    /** Paths that must 404 rather than receive the shell, since they are API surface. */
    private static final String[] NEVER_SPA = {"api/", "actuator/", "v3/api-docs"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_ROOT)
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    private static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            // A real endpoint that simply does not exist must stay a 404, not become HTML.
            for (String prefix : NEVER_SPA) {
                if (resourcePath.startsWith(prefix)) {
                    return null;
                }
            }
            return SHELL;
        }
    }
}
