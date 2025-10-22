package cn.addenda.loglearn;

import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class LogClassLoader extends URLClassLoader {

  @Getter
  private static LogClassLoader DEFAULT_LOADER;
  private final List<String> logPrefixList = new ArrayList<>();

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public LogClassLoader(ClassLoader parent) {
    super(findJarUrls(), parent);

    logPrefixList.add("org.slf4j.");
    logPrefixList.add("org.apache.logging.log4j.");
    logPrefixList.add("org.apache.logging.slf4j.");
  }

  private static URL[] findJarUrls() {
    List<URL> urls = new ArrayList<>();
    File agentJarDir = AgentPackagePath.getPath();
    System.out.println("Agent base directory: " + agentJarDir);
    File logLibDir = new File(new File(agentJarDir, "lib"), "log");

    if (logLibDir.exists() && logLibDir.isDirectory()) {
      File[] jars = logLibDir.listFiles((dir, name) -> name.endsWith(".jar"));

      if (jars != null) {
        for (File jar : jars) {
          try {
            urls.add(jar.toURI().toURL());
            System.out.println("Found isolated JAR: " + jar.getName());
          } catch (MalformedURLException e) {
            System.err.println("Failed to create URL for JAR: " + jar.getAbsolutePath());
          }
        }
      }
    }
    return urls.toArray(new URL[0]);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
      Class<?> loadedClass = findLoadedClass(name);
      if (loadedClass != null) {
        return loadedClass;
      }

      boolean shouldIsolate = false;
      for (String prefix : logPrefixList) {
        if (name.startsWith(prefix)) {
          shouldIsolate = true;
          break;
        }
      }

      if (shouldIsolate) {
        loadedClass = findClass(name);
        if (resolve) {
          resolveClass(loadedClass);
        }
        return loadedClass;
      }

      return super.loadClass(name, resolve);
    }
  }

  @Override
  public URL getResource(String name) {
    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix.replaceAll("\\.", "/"))) {
        return findResource(name);
      }
    }
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix.replaceAll("\\.", "/"))) {
        return findResources(name);
      }
    }
    return super.getResources(name);
  }

  public static synchronized void initDefaultLoader() {
    if (DEFAULT_LOADER == null) {
      ClassLoader extensionClassLoader = ClassLoader.getSystemClassLoader().getParent();
      DEFAULT_LOADER = new LogClassLoader(extensionClassLoader);
    }
  }

}
