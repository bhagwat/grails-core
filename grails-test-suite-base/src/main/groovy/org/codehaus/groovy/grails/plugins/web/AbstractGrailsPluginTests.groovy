package org.codehaus.groovy.grails.plugins.web

import grails.util.Holders
import grails.util.Metadata
import grails.util.MockHttpServletResponse
import grails.web.CamelCaseUrlConverter
import grails.web.UrlConverter
import org.codehaus.groovy.grails.commons.ClassPropertyFetcher
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.spring.GrailsRuntimeConfigurator
import org.codehaus.groovy.grails.commons.spring.WebRuntimeSpringConfiguration
import org.codehaus.groovy.grails.plugins.DefaultGrailsPlugin
import org.codehaus.groovy.grails.plugins.MockGrailsPluginManager
import org.codehaus.groovy.grails.support.MockApplicationContext
import org.codehaus.groovy.grails.support.MockResourceLoader
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.CachedIntrospectionResults
import org.springframework.context.ApplicationContext
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext

import javax.servlet.ServletContext

abstract class AbstractGrailsPluginTests extends GroovyTestCase {

    ServletContext servletContext
    GrailsWebRequest webRequest
    MockHttpServletRequest request
    MockHttpServletResponse response
    def gcl = new GroovyClassLoader()
    def ga
    def mockManager
    def ctx
    def originalHandler
    def springConfig
    ApplicationContext appCtx
    def pluginsToLoad = []
    def resolver = new PathMatchingResourcePatternResolver()

    protected void onSetUp() {}

    protected final void setUp() {
        super.setUp()

        ExpandoMetaClass.enableGlobally()
        Holders.clear()
        ClassPropertyFetcher.clearClassPropertyFetcherCache()
        CachedIntrospectionResults.clearClassLoader(this.getClass().classLoader)
        CachedIntrospectionResults.clearClassLoader(Thread.currentThread().getContextClassLoader())

        ctx = new MockApplicationContext()
        onSetUp()
        ga = new DefaultGrailsApplication(gcl.getLoadedClasses(),gcl)
        ga.metadata[Metadata.APPLICATION_NAME] = getClass().name
        ctx.registerMockBean UrlConverter.BEAN_NAME, new CamelCaseUrlConverter()
        mockManager = new MockGrailsPluginManager(ga)
        def dependentPlugins = pluginsToLoad.collect { new DefaultGrailsPlugin(it, ga)}
        dependentPlugins.each{ mockManager.registerMockPlugin(it); it.manager = mockManager }
        mockManager.doArtefactConfiguration()
        ga.initialise()
        ga.setApplicationContext(ctx)
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, ga)
        ctx.registerMockBean(GrailsApplication.CLASS_LOADER_BEAN, gcl)

        ctx.registerMockBean("manager", mockManager)

        def configurator = new GrailsRuntimeConfigurator(ga, ctx)
        configurator.pluginManager = mockManager
        ctx.registerMockBean(GrailsRuntimeConfigurator.BEAN_ID, configurator)

        springConfig = new WebRuntimeSpringConfiguration(ctx)
        servletContext = new MockServletContext(new MockResourceLoader())
        springConfig.servletContext = servletContext
        mockManager.registerProvidedArtefacts(ga)
        dependentPlugins*.doWithRuntimeConfiguration(springConfig)

        ga.mainContext = springConfig.getUnrefreshedApplicationContext()
        appCtx = springConfig.getApplicationContext()
        
        mockManager.applicationContext = appCtx
        servletContext.setAttribute(GrailsApplicationAttributes.APPLICATION_CONTEXT, appCtx)
        dependentPlugins*.doWithDynamicMethods(appCtx)
        dependentPlugins*.doWithApplicationContext(appCtx)

        Holders.setGrailsApplication(ga)
        Holders.setConfig(ga.config)
    }

    protected final void tearDown() {
        ga.mainContext.close()
        pluginsToLoad = []
        ExpandoMetaClass.disableGlobally()
        Holders.clear()
    }
}
