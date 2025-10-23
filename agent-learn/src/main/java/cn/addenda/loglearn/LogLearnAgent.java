package cn.addenda.loglearn;

import org.slf4j.Logger;

import java.lang.instrument.Instrumentation;

public class LogLearnAgent {

  static Logger log;

  static {
    // dist/lib/log/log4j2.xml
    log = MyLoggerFactory.getLogger(LogLearnAgent.class);

    // app/src/main/resources/log4j2.xml
//    log = LoggerFactory.getLogger(LogLearnAgent.class);

  }

  public static void premain(String args, Instrumentation instrumentation) {

    log.info("进入到premain, SkywalkingLearnAgent.class.classLoader = {}, args:{}",
            LogLearnAgent.class.getClassLoader(), args);

  }

}
