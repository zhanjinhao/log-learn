package cn.addenda.loglearn;

import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;

public class MyLoggerFactory {

  static Class<?> loggerFactoryClass;
  // 添加Logger接口的引用
  private static Class<?> loggerInterface;

  static Method getLoggerString;
  static Method getLoggerClass;

  static Class<?> logManagerClass;
  static Class<?> loggerContextClass;

  static {
    try {
      LogClassLoader.initDefaultLoader();
      LogClassLoader defaultLoader = LogClassLoader.getDEFAULT_LOADER();

      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(defaultLoader);

      try {
        loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory", true, defaultLoader);
        loggerInterface = Class.forName("org.slf4j.Logger", true, defaultLoader);
        logManagerClass = Class.forName("org.apache.logging.log4j.LogManager", true, defaultLoader);
        loggerContextClass = Class.forName("org.apache.logging.log4j.core.LoggerContext", true, defaultLoader);

        getLoggerString = loggerFactoryClass.getDeclaredMethod("getLogger", String.class);
        getLoggerClass = loggerFactoryClass.getDeclaredMethod("getLogger", Class.class);

        File agentJarDir = AgentPackagePath.getPath();

        File conFile = new File(new File(new File(AgentPackagePath.getPath(), "lib"), "log"), "log4j2.xml");

//        ConfigurationSource source = new ConfigurationSource(new FileInputStream(conFile), conFile);
//        Configuration configuration = new XmlConfiguration(null, source);
//        Configurator.initialize(configuration);

        // config dist/lib/log/log4j2.xml
        Method getContextMethod = logManagerClass.getDeclaredMethod("getContext", ClassLoader.class, boolean.class, URI.class);
        Object logContext = getContextMethod.invoke(null, null, false, conFile.toURI());
      } finally {
        Thread.currentThread().setContextClassLoader(contextClassLoader);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static Logger getLogger(Class<?> clazz) {
    try {
      Object invoke = getLoggerClass.invoke(null, clazz);

      // 使用代理模式包装返回的对象，避免类型转换问题
      if (invoke != null) {
        return createLoggerProxy(invoke);
      }
      throw new RuntimeException(String.format("Failed to create logger %s instance", clazz));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // 创建代理Logger实例来避免类加载器问题
  private static Logger createLoggerProxy(Object loggerInstance) {
    // 实现代理逻辑，将方法调用转发给实际的logger实例
    // 这样可以绕过类加载器类型检查问题
    return (Logger) Proxy.newProxyInstance(
            MyLoggerFactory.class.getClassLoader(),
            new Class[]{Logger.class},
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // 在目标对象上查找匹配的方法
                Method targetMethod = loggerInstance.getClass().getMethod(
                        method.getName(), method.getParameterTypes());
                return targetMethod.invoke(loggerInstance, args);
              }
            }
    );
  }

  public static Logger getLogger(String clazz) {
    try {
      Object invoke = getLoggerString.invoke(null, clazz);

      // 使用代理模式包装返回的对象，避免类型转换问题
      if (invoke != null) {
        return createLoggerProxy(invoke);
      }
      throw new RuntimeException(String.format("Failed to create logger %s instance", clazz));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
