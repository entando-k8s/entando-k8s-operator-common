package org.entando.kubernetes.cdi;

import io.fabric8.kubernetes.api.model.HasMetadata;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import org.apache.logging.log4j.util.Strings;
import org.entando.kubernetes.model.EntandoCustomResource;

@Interceptor
@K8SLogger
public class KubernetesRestInterceptor {

    private static String format(HasMetadata entandoCustomResource) {
        return String.format(" with %s: %s/%s", entandoCustomResource.getKind(), entandoCustomResource.getMetadata().getNamespace(),
                entandoCustomResource.getMetadata().getName());
    }

    @AroundInvoke
    @SuppressWarnings("squid:S2139")//Because it is common practice to log and rethrow an exception in a logging interceptor
    public Object logMethodEntry(InvocationContext invocationContext) throws Exception {
        Logger logger = Logger.getLogger(invocationContext.getMethod().getDeclaringClass().getName());
        logger.log(Level.SEVERE, () -> {
            StringBuilder message = new StringBuilder(
                    String.format("Entering method %s in class %s", invocationContext.getMethod().getName(),
                            invocationContext.getMethod().getDeclaringClass().getName()));
            Optional<EntandoCustomResource> first = Arrays.stream(invocationContext.getParameters())
                    .filter(EntandoCustomResource.class::isInstance)
                    .map(EntandoCustomResource.class::cast).findFirst();
            if (first.isPresent()) {
                message.append(format(first.get()));
            }
            Optional<HasMetadata> second = Arrays.stream(invocationContext.getParameters())
                    .filter(o -> o instanceof HasMetadata && o.getClass().getName().startsWith("io.fabric8.kubernetes"))
                    .map(HasMetadata.class::cast).findFirst();
            if (second.isPresent()) {
                if (first.isPresent()) {
                    message.append(" and");
                }
                message.append(format(second.get()));
            }
            if (!(first.isPresent() || second.isPresent())) {
                message.append(" with ").append(Strings.join(Arrays.asList(invocationContext.getParameters()), ','));
            }
            return message.toString();
        });
        try {
            return invocationContext.proceed();
        } catch (Exception e) {
            logger.log(Level.SEVERE, e, () -> String.format("Failure executing method %s in class %s",
                    invocationContext.getMethod().getName(),
                    invocationContext.getMethod().getDeclaringClass().getName()));
            throw e;
        } finally {
            logger.log(Level.SEVERE, () ->
                    String.format("Exiting method %s in class %s", invocationContext.getMethod().getName(),
                            invocationContext.getMethod().getDeclaringClass().getName()));

        }
    }
}
