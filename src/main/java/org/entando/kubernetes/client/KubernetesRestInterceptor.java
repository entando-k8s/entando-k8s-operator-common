package org.entando.kubernetes.client;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.logging.log4j.util.Strings;
import org.entando.kubernetes.model.EntandoCustomResource;

public class KubernetesRestInterceptor implements InvocationHandler {

    private Object delegate;

    public KubernetesRestInterceptor(Object delegate) {
        this.delegate = delegate;
    }

    private static String format(HasMetadata entandoCustomResource) {
        return String.format(" with %s: %s/%s", entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getNamespace(),
                entandoCustomResource.getMetadata().getName());
    }

    @SuppressWarnings("squid:S2139")//Because it is common practice to log and rethrow an exception in a logging interceptor
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Logger logger = Logger.getLogger(method.getDeclaringClass().getName());
        logger.log(Level.SEVERE, () -> {
            StringBuilder message = new StringBuilder(
                    String.format("Entering method %s in class %s", method.getName(),
                            method.getDeclaringClass().getName()));
            Optional<EntandoCustomResource> first = Arrays.stream(args)
                    .filter(EntandoCustomResource.class::isInstance)
                    .map(EntandoCustomResource.class::cast).findFirst();
            if (first.isPresent()) {
                message.append(format(first.get()));
            }
            Optional<HasMetadata> second = Arrays.stream(args)
                    .filter(o -> o instanceof HasMetadata && o.getClass().getName().startsWith("io.fabric8.kubernetes"))
                    .map(HasMetadata.class::cast).findFirst();
            if (second.isPresent()) {
                if (first.isPresent()) {
                    message.append(" and");
                }
                message.append(format(second.get()));
            }
            if (!(first.isPresent() || second.isPresent())) {
                message.append(" with ").append(Strings.join(Arrays.asList(args), ','));
            }
            return message.toString();
        });
        try {
            return method.invoke(delegate, args);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e, () -> String.format("Failure executing method %s in class %s",
                    method.getName(),
                    method.getDeclaringClass().getName()));
            throw e;
        } finally {
            logger.log(Level.SEVERE, () ->
                    String.format("Exiting method %s in class %s", method.getName(),
                            method.getDeclaringClass().getName()));

        }
    }

}
