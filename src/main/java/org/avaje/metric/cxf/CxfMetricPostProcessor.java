package org.avaje.metric.cxf;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jws.WebService;
import javax.xml.ws.BindingProvider;

import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.InterceptorProvider;
import org.apache.cxf.message.Message;
import org.avaje.metric.Clock;
import org.avaje.metric.MetricManager;
import org.avaje.metric.MetricName;
import org.avaje.metric.TimedMetricGroup;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;


/**
 * Spring bean post processor that finds CXF Endpoint's and CXF client proxies
 * and registers the appropriate interceptor's to collect metrics.
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

      // Create a base MetricName and create the TimedMetricGroup. All the metrics
      // for this endpoint share the common "base name" and the operation name will be
      // appended to that.
      MetricName baseName = getServiceBaseName(implementor);
      TimedMetricGroup timedMetricGroup = MetricManager.getTimedMetricGroup(baseName, rateUnit, Clock.defaultClock());
      
      // register the interceptors to this endpoint
      ResponseTimeMessageInInterceptor inInterceptor = new ResponseTimeMessageInInterceptor(timedMetricGroup);
      ResponseTimeMessageOutInterceptor outInterceptor = new ResponseTimeMessageOutInterceptor(timedMetricGroup);
      registerInterceptors(prov, inInterceptor, outInterceptor);

      if (logger.isLoggable(Level.FINE)) {
        logger.fine("Registered CXF Endpoint: " + implementor.getClass().getSimpleName());
      }
      return true;
    }
    return false;
  }

  private MetricName getServiceBaseName(Object implementor) {
    String simpleName = implementor.getClass().getSimpleName();
    int dollarPos = simpleName.indexOf('$');
    if (dollarPos > 0) {
      simpleName = simpleName.substring(0,dollarPos);
    }
    return new MetricName("webservice","server",simpleName);
  }
  
  private boolean isCxfClientProxy(Object bean) {

    Class<?> clazz = bean.getClass();

    Class<?>[] interfaces = clazz.getInterfaces();
    for (Class<?> class1 : interfaces) {
      if (class1.equals(BindingProvider.class)) {

        Class<?> webserviceClass = determineInterface(clazz);
        registerClientInterceptors(bean, webserviceClass);
        return true;
      }
    }
    return false;
  }

  private void registerClientInterceptors(Object bean, Class<?> webserviceClass) {

    String name = (webserviceClass == null) ? bean.getClass().getSimpleName() : webserviceClass.getSimpleName();
    Client cxfClient = ClientProxy.getClient(bean);

    MetricName baseName = new MetricName("webservice.client", name, "placeholder", null);
    TimedMetricGroup timedMetricGroup = MetricManager.getTimedMetricGroup(baseName, rateUnit, Clock.defaultClock());

    // Add In and Out Interceptor's for normal processing and faults
    ResponseTimeMessageInInterceptor inIntercept = new ResponseTimeMessageInInterceptor(timedMetricGroup);
    ResponseTimeMessageOutInterceptor outIntercept = new ResponseTimeMessageOutInterceptor(timedMetricGroup);
    
    registerInterceptors(cxfClient, inIntercept, outIntercept);
    
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Registered CXF Client: " + name);
    }
  }
  
  private void registerInterceptors(InterceptorProvider prov, Interceptor<? extends Message> in, Interceptor<? extends Message> out) {
    prov.getInInterceptors().add(in);
    prov.getInFaultInterceptors().add(in);
    prov.getOutInterceptors().add(out);
    prov.getOutFaultInterceptors().add(out);
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
