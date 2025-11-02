package cn.addenda.loglearn.test;

import cn.addenda.loglearn.AgentPackagePath;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 增加日志
 */
public class ClassMultiLoadingClassLoader extends URLClassLoader {

  @Getter
  private static ClassMultiLoadingClassLoader DEFAULT_LOADER;
  private final List<String> logPrefixList = new ArrayList<>();

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public ClassMultiLoadingClassLoader(ClassLoader parent) {
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
      Class<?> loadedClass = null;
//      loadedClass = findLoadedClass(name);
//      if (loadedClass != null) {
//        return loadedClass;
//      }

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
  public URL findResource(String name) {
    System.out.println("findResource : " + name);
    URL resource = super.findResource(name);
    if (resource != null) {
      System.out.println("findResource success : " + resource);
    }
    return resource;
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    System.out.println("findResources : " + name);
    Enumeration<URL> resources = super.findResources(name);
    List<URL> urlList = new ArrayList<>();
    while (resources.hasMoreElements()) {
      urlList.add(resources.nextElement());
    }
    if (!urlList.isEmpty()) {
      System.out.println("findResources success : " + urlList);
    }

    return super.findResources(name);
  }

  @Override
  public URL getResource(String name) {
    System.out.println("getResource : " + name);

    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix.replaceAll("\\.", "/"))) {
        return findResource(name);
      }
    }
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    System.out.println("getResources : " + name);

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
      DEFAULT_LOADER = new ClassMultiLoadingClassLoader(extensionClassLoader);
    }
  }

}
