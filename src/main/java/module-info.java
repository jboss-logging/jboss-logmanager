import org.jboss.logmanager.ConfiguratorFactory;
import org.jboss.logmanager.JBossLoggerFinder;
import org.jboss.logmanager.LogContextConfigurator;
import org.jboss.logmanager.LogContextInitializer;
import org.jboss.logmanager.LogManager;
import org.jboss.logmanager.MDCProvider;
import org.jboss.logmanager.NDCProvider;
import org.jboss.logmanager.configuration.DefaultConfiguratorFactory;

module org.jboss.logmanager {
    requires transitive java.logging;

    requires io.smallrye.common.constraint;
    requires io.smallrye.common.cpu;
    requires io.smallrye.common.expression;
    requires io.smallrye.common.net;
    requires io.smallrye.common.os;
    requires io.smallrye.common.ref;

    requires static java.xml;
    requires static jakarta.json;
    requires static org.jboss.modules;

    exports org.jboss.logmanager;
    exports org.jboss.logmanager.configuration;
    exports org.jboss.logmanager.configuration.filters;
    exports org.jboss.logmanager.errormanager;
    exports org.jboss.logmanager.filters;
    exports org.jboss.logmanager.formatters;
    exports org.jboss.logmanager.handlers;

    provides java.util.logging.LogManager with LogManager.Provider;
    provides System.LoggerFinder with JBossLoggerFinder;
    provides ConfiguratorFactory with DefaultConfiguratorFactory;

    uses ConfiguratorFactory;
    uses LogContextConfigurator;
    uses LogContextInitializer;
    uses MDCProvider;
    uses NDCProvider;
}
