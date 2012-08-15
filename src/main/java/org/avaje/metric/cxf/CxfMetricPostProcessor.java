package org.avaje.metric.cxf;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.xml.ws.BindingProvider;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.avaje.metric.Clock;
import org.avaje.metric.MetricManager;
import org.avaje.metric.MetricName;
import org.avaje.metric.TimedMetricGroup;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;


/**
 * Spring bean post processor that finds CXF Endpoints and CXF client proxies
 * and registers the appropriate interceptors to collect metrics.
 */
public class CxfMetricPostProcessor implements BeanPostProcessor {

  private static final Logger logger = Logger.getLogger(CxfMetricPostProcessor.class.getName());

  protected final TimeUnit rateUnit;

  /**
   * Create with a rateUnit of Minutes.
   */
  public CxfMetricPostProcessor() {
    this(TimeUnit.MINUTES);
  }

  /**
   * Create with a specified rateUnit.
   */
  public CxfMetricPostProcessor(TimeUnit rateUnit) {
    this.rateUnit = rateUnit;
  }

  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

    if (isCxfEndpoint(bean)) {
      return bean;
    }

    if (isCxfClientProxy(bean)) {
      return bean;
    }

    return bean;
  }

  private boolean isCxfEndpoint(Object bean) {

    if (bean instanceof InterceptorProvider && bean instanceof javax.xml.ws.Endpoint) {

      javax.xml.ws.Endpoint endpoint = (javax.xml.ws.Endpoint) bean;
      Object implementor = endpoint.getImplementor();

      InterceptorProvider prov = (InterceptorProvider) bean;

      MetricName baseName = new MetricName(implementor.getClass(), null);
      //MetricNameCache nameCache = MetricManager.getMetricNameCache(implementor.getClass());
      TimedMetricGroup timedMetricGroup = MetricManager.getTimedMetricGroup(baseName, rateUnit, Clock.defaultClock());
      
      prov.getInInterceptors().add(new ResponseTimeMessageInInterceptor(timedMetricGroup));
      prov.getOutInterceptors().add(new ResponseTimeMessageOutInterceptor(timedMetricGroup));

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Registered CXF Endpoint: " + implementor.getClass().getSimpleName());
      }
      return true;
    }
    return false;
  }

  private boolean isCxfClientProxy(Object bean) {

    Class<?> clazz = bean.getClass();

    Class<?>[] interfaces = clazz.getInterfaces();
    for (Class<?> class1 : interfaces) {
      if (class1.equals(BindingProvider.class)) {

        Class<?> webserviceClass = determineInterface(clazz);
        registerCxfInterceptors(bean, webserviceClass);
        return true;
      }
    }
    return false;
  }

  private void registerCxfInterceptors(Object bean, Class<?> webserviceClass) {

    String name = (webserviceClass == null) ? bean.getClass().getSimpleName() : webserviceClass.getSimpleName();
    Client cxfClient = ClientProxy.getClient(bean);

    MetricName baseName = new MetricName("webservice.client", name, "placeholder", null);
    TimedMetricGroup timedMetricGroup = MetricManager.getTimedMetricGroup(baseName, rateUnit, Clock.defaultClock());

    cxfClient.getInInterceptors().add(new ResponseTimeMessageInInterceptor(timedMetricGroup));
    cxfClient.getOutInterceptors().add(new ResponseTimeMessageOutInterceptor(timedMetricGroup));
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Registered CXF Client: " + name);
    }
  }

  private Class<?> determineInterface(Class<?> clazz) {

    Class<?>[] interfaces = clazz.getInterfaces();
    for (Class<?> class1 : interfaces) {
      if (class1.isAnnotationPresent(WebService.class)) {
        return class1;
      }
    }
    return null;
  }
}
