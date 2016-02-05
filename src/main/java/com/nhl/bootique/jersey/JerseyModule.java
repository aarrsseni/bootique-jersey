package com.nhl.bootique.jersey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Feature;

import org.glassfish.jersey.server.ResourceConfig;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.nhl.bootique.ConfigModule;
import com.nhl.bootique.config.ConfigurationFactory;
import com.nhl.bootique.jetty.JettyModule;
import com.nhl.bootique.jetty.MappedServlet;

// TODO: should we turn this into ConfigModule? we'll be able to start Jersey from YAML then
public class JerseyModule extends ConfigModule {

	private String servletPath = "/*";
	private Class<? extends Application> application;
	private Collection<Class<?>> resources = new HashSet<>();
	private Collection<String> packageRoots = new HashSet<>();

	/**
	 * @param binder
	 *            DI binder passed to the Module that invokes this method.
	 * @since 0.11
	 * @return returns a {@link Multibinder} for JAX-RS Features.
	 */
	public static Multibinder<Feature> contributeFeatures(Binder binder) {
		return Multibinder.newSetBinder(binder, Feature.class);
	}

	public <T extends Application> JerseyModule application(Class<T> application) {
		this.application = application;
		return this;
	}

	public JerseyModule servletPath(String servletPath) {
		this.servletPath = servletPath;
		return this;
	}

	public JerseyModule resource(Class<?> resourceType) {
		resources.add(resourceType);
		return this;
	}

	public JerseyModule packageRoot(Package aPackage) {
		packageRoots.add(aPackage.getName());
		return this;
	}

	public JerseyModule packageRoot(Class<?> classFromPackage) {
		String name = classFromPackage.getName();
		int dot = name.lastIndexOf('.');
		if (dot <= 0) {
			throw new IllegalArgumentException("Class is in default package - unsupported");
		}

		packageRoots.add(name.substring(0, dot));
		return this;
	}

	@Override
	public void configure(Binder binder) {

		JettyModule.contributeServlets(binder).addBinding().to(Key.get(MappedServlet.class, JerseyServlet.class));

		// trigger extension points creation and provide default contributions
		JerseyModule.contributeFeatures(binder);
	}

	@Singleton
	@Provides
	private ResourceConfig createResourceConfig(Injector injector, Set<Feature> features) {
		ResourceConfig config = application != null ? ResourceConfig.forApplicationClass(application)
				: new ResourceConfig();

		packageRoots.forEach(p -> config.packages(true, p));
		resources.forEach(r -> config.register(r));
		features.forEach(f -> config.register(f));

		// register Guice Injector as a service in Jersey HK2, and
		// GuiceBridgeFeature as a
		GuiceBridgeFeature.register(config, injector);

		return config;
	}

	@JerseyServlet
	@Provides
	@Singleton
	private MappedServlet createJerseyServlet(ConfigurationFactory configFactory, ResourceConfig config) {
		return configFactory.config(JerseyServletFactory.class, configPrefix).initServletPathIfNotSet(servletPath)
				.createJerseyServlet(config);
	}
}
