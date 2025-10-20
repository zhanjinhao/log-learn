package cn.addenda.loglearn;

import lombok.Getter;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 用于加载插件和插件的拦截器
 */
public class LogClassLoader extends ClassLoader {

  /**
   * 用于加载日志组件的加载器
   */
  @Getter
  private static LogClassLoader DEFAULT_LOADER;

  /**
   * 自定义类加载器加载类的路径
   */
  private File classpath;
  private List<Jar> allJarList;
  private ReentrantLock jarScanLock = new ReentrantLock();

  public LogClassLoader(ClassLoader parent) {
    super(parent);

    File agentJarDir = AgentPackagePath.getPath();
    System.out.println(String.format("logLibDir: %s", agentJarDir));

    classpath = new File(new File(agentJarDir, "lib"), "log");
    getAllJarList();
  }

  public static void initDefaultLoader() {
    if (DEFAULT_LOADER == null) {
      DEFAULT_LOADER = new LogClassLoader(LogClassLoader.class.getClassLoader());
    }
  }

  private static List<String> logPrefixList = new ArrayList<>();

  static {
    logPrefixList.add("org.slf4j.");
    logPrefixList.add("org.apache.logging.log4j.");
    logPrefixList.add("org.apache.logging.slf4j.");
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    for (String logPrefix : logPrefixList) {
      if (name.startsWith(logPrefix)) {
        Class<?> aClass = doFindClass(name);
        if (resolve) {
          resolveClass(aClass);
        }
      }
    }

    return super.loadClass(name, resolve);
  }

  /**
   * loadClass --> findClass（自定义自己的类加载逻辑） --> defineClass
   */
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    return super.findClass(name);
  }

  private Class<?> doFindClass(String name) throws ClassNotFoundException {
    List<Jar> _allJarList = getAllJarList();

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

  private List<Jar> getAllJarList() {
    if (allJarList == null) {
      jarScanLock.lock();
      try {
        if (allJarList == null) {
          allJarList = doGetJars();
        }
      } finally {
        jarScanLock.unlock();
      }
    }
    return allJarList;
  }

  private List<Jar> doGetJars() {
    List<Jar> _allJarList = new ArrayList<>();

    if (classpath.exists() && classpath.isDirectory()) {
      String[] list = classpath.list(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          return name.endsWith(".jar");
        }
      });
      if (list == null || list.length == 0) {
        return _allJarList;
      }
      for (String s : list) {
        File jarSourceFile = new File(classpath, s);
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

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    List<URL> allResources = new ArrayList<>();
    for (Jar jar : allJarList) {
      JarEntry jarEntry = jar.jarFile.getJarEntry(name);
      if (jarEntry == null) {
        continue;
      }
      try {
        URL url = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name);
        allResources.add(url);
      } catch (Exception e) {
        System.out.println(String.format("find file {} error", name));
        e.printStackTrace();
      }
    }
    Iterator<URL> iterator = allResources.iterator();
    return new Enumeration<URL>() {
      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public URL nextElement() {
        return iterator.next();
      }
    };
  }

  @Override
  public URL getResource(String name) {
    for (Jar jar : allJarList) {
      JarEntry jarEntry = jar.jarFile.getJarEntry(name);
      if (jarEntry == null) {
        continue;
      }
      try {
        // 返回第一个
        URL url = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + name);
        return url;
      } catch (Exception e) {
        System.out.println(String.format("find file {} error", name));
        e.printStackTrace();
      }
    }
    return null;
  }

}
