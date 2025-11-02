package cn.addenda.loglearn;

import lombok.Getter;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 将class的加载换成我自己的实现，但不继承URLClassLoader，同时指定父加载器为SystemClassLoader。Application可以，但是OnlyMainApp不可以。
 *
 * 1、是哪些文件加载失败，导致的异常
 * 2、为什么URLClassLoader可以呢，
 */
public class LogClassLoader5 extends ClassLoader {

  @Getter
  private static LogClassLoader5 DEFAULT_LOADER;
  private final List<String> logPrefixList = new ArrayList<>();

  List<Jar> jars;

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public LogClassLoader5(ClassLoader parent) {
    super(parent);

    jars = doGetJars();
    logPrefixList.add("org.slf4j.");
    logPrefixList.add("org.apache.logging.log4j.");
    logPrefixList.add("org.apache.logging.slf4j.");
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
        loadedClass = doFindClass(name);
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
    System.out.println("getResource: " + name);
    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix.replaceAll("\\.", "/"))) {
        return findResource(name);
      }
    }
    return super.getResource(name);
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    System.out.println("getResources: " + name);
    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix.replaceAll("\\.", "/"))) {
        return findResources(name);
      }
    }
    return super.getResources(name);
  }

  public static synchronized void initDefaultLoader() {
    if (DEFAULT_LOADER == null) {
//      ClassLoader extensionClassLoader = ClassLoader.getSystemClassLoader().getParent();
      ClassLoader extensionClassLoader = ClassLoader.getSystemClassLoader();
      DEFAULT_LOADER = new LogClassLoader5(extensionClassLoader);
    }
  }

  private Class<?> doFindClass(String name) throws ClassNotFoundException {
    List<Jar> _allJarList = jars;

    String concat = name.replace(".", "/").concat(".class");
    for (Jar jar : _allJarList) {
      JarEntry jarEntry = jar.jarFile.getJarEntry(concat);
      if (jarEntry == null) {
        continue;
      }
      try {
        URL url = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + concat);
        byte[] byteArray = IOUtils.toByteArray(url);
        return defineClass(name, byteArray, 0, byteArray.length);
      } catch (Exception e) {
        System.out.println(String.format("find class %s error", name));
      }
    }
    throw new ClassNotFoundException("can not find " + name);
  }


  private List<Jar> doGetJars() {
    List<Jar> _allJarList = new ArrayList<>();
    File agentJarDir = AgentPackagePath.getPath();
    System.out.println("Agent base directory: " + agentJarDir);
    File logLibDir = new File(new File(agentJarDir, "lib"), "log");

    if (logLibDir.exists() && logLibDir.isDirectory()) {
      String[] list = logLibDir.list((dir, name) -> name.endsWith(".jar"));
      if (list == null || list.length == 0) {
        return _allJarList;
      }
      for (String s : list) {
        File jarSourceFile = new File(logLibDir, s);
        try {
          Jar jar = new Jar(new JarFile(jarSourceFile), jarSourceFile);
          _allJarList.add(jar);
          System.out.println(String.format("load jar %s success.", jarSourceFile));
        } catch (Exception e) {
          System.out.println(String.format("jar %s load fail.", jarSourceFile));
          e.printStackTrace();
        }
      }
    }
    return _allJarList;
  }

  private static class Jar {
    /**
     * jar文件对对应的jarFile对象
     */
    private final JarFile jarFile;
    /**
     * jar文件
     */
    private final File sourceFile;

    public Jar(JarFile jarFile, File sourceFile) {
      this.jarFile = jarFile;
      this.sourceFile = sourceFile;
    }
  }

}
