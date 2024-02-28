package com.almod.jmx;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Примеры по работе с JMX: просмотр информации приложения, вызов методов у классов
 */
public class Main {
    public static void main(String[] args) throws Exception {
        // Если для jmx соединения включено ssl, то необходимо определить сертификаты
        installSSL();

        // Создание JMX соединение
        String serviceURL = "service:jmx:rmi://host:port/jndi/rmi://host:portjmx/jmxrmi";
        String login = "login";
        String password = "password";
        MBeanServerConnection mBeanServerConnection = createJMXConnection(serviceURL, login, password);


        /*
        Основная информация по доменам
        Пример:
        Domains:
	        Domain = JMImplementation
	        Domain = com.oracle.jdbc
	        Domain = com.sun.management
	        Domain = java.lang
	        Domain = java.nio
	        Domain = java.util.logging
	        Domain = oracle.ucp.admin
	        Domain = oracle.ucp.admin.UniversalConnectionPoolMBean
	        Domain = org.apache.camel
	        Domain = quartz
        */
        System.out.println("\nDomains:");
        String domains[] = mBeanServerConnection.getDomains();
        Arrays.sort(domains);
        for (String domain : domains) {
            System.out.println("\tDomain = " + domain);
        }


        /*
        Пути ко всем ресурсам JMX
        Для JMX порядок не имеет значения, к примеру ниже значения будут равны
        org.apache.activemq:type=Broker,brokerName=localBroker,destinationType=Queue == destinationType=Queue,org.apache.activemq:type=Broker,brokerName=localBroker

        Пример (часть вывода):
        oracle.ucp.admin.UniversalConnectionPoolMBean:name=UniversalConnectionPoolManager(1359891833)-a2f5f0647-8-sonic
        oracle.ucp.admin.UniversalConnectionPoolMBean:name=UniversalConnectionPoolManager(1359891833)-a2f5f0647-12-sonic
        java.lang:type=ClassLoading
        java.lang:name=PS Survivor Space,type=MemoryPool
        java.util.logging:type=Logging
        java.lang:name=PS Old Gen,type=MemoryPool
        com.sun.management:type=HotSpotDiagnostic
        */
        Set<ObjectInstance> domainObjectNames = mBeanServerConnection.queryMBeans(null, null);
        for(ObjectInstance ob : domainObjectNames) {
            String path = ob.getObjectName().getCanonicalName();
            System.out.println(path);
        }

        /*
        Точечное получение информации из аттрибута
        Пример:
        81256
        */
        ObjectName objectName = new ObjectName("java.lang:type=ClassLoading");
        System.out.println(mBeanServerConnection.getAttribute(objectName, "TotalLoadedClassCount"));



        /*
        Точечное получение информации из аттрибута, у которого тип CompositeData
        Пример:
        Used / committed
        4320875352,00 / 11854151680,00
        36%
        */
        ObjectName objectName2 = new ObjectName("java.lang:type=Memory");
        CompositeData compositeData = (CompositeData) mBeanServerConnection.getAttribute(objectName2, "HeapMemoryUsage");

        Double used = Double.parseDouble(compositeData.get("used").toString());
        Double committed = Double.parseDouble(compositeData.get("committed").toString());
        int heapPercent = (int) Math.round((used / committed) * 100);

        System.out.println("Used / committed");
        System.out.printf("%.2f / %.2f\n", used, committed);
        System.out.println(heapPercent + "%");



        /*
        Получение информации о методах
        Пример:
        getThreadCpuTime ([J p0)
        getThreadCpuTime (long p0)
        getThreadUserTime ([J p0)
        getThreadUserTime (long p0)
        getThreadAllocatedBytes ([J p0)
        getThreadAllocatedBytes (long p0)
        getThreadInfo ([J p0)
        getThreadInfo (long p0)
        getThreadInfo ([J p0, boolean p1, boolean p2)
        getThreadInfo ([J p0, int p1)
        getThreadInfo (long p0, int p1)
        findMonitorDeadlockedThreads ()
        resetPeakThreadCount ()
        findDeadlockedThreads ()
        dumpAllThreads (boolean p0, boolean p1)
        */
        ObjectName objectName3 = new ObjectName("java.lang:type=Threading");
        MBeanInfo mBeanInfo = mBeanServerConnection.getMBeanInfo(objectName3);
        Arrays.stream(mBeanInfo.getOperations()).forEach(Main::dumpOperation);


        /*
         Выполнение метода без параметров
         Пример:
         Java HotSpot(TM) 64-Bit Server VM version 25.101-b13
         JDK 8.0_101
        */
        ObjectName objectName4 = new ObjectName("com.sun.management:type=DiagnosticCommand");
        String result = String.valueOf(mBeanServerConnection.invoke(objectName4, "vmVersion", null, null));
        System.out.println(result);


        /*
        Выполнение метода с параметром
        (Проверка, включена ли генерация heapDump в случае OutOfMemoryError)
        Пример:
        true
        */
        ObjectName objectName5 = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
        Object [] paramsForInvoke = { "HeapDumpOnOutOfMemoryError" };
        String [] signatureForInvoke = { String.class.getName() };
        CompositeData compositeData2 = (CompositeData) mBeanServerConnection.invoke(objectName5, "getVMOption", paramsForInvoke, signatureForInvoke);
        System.out.println(compositeData2.get("value"));
    }

    static void installSSL() {
        String keyPath = "path/*.jks ";
        String keyPassword = "password";
        System.setProperty("javax.net.ssl.keyStore", keyPath);
        System.setProperty("javax.net.ssl.keyStorePassword", keyPassword);
        System.setProperty("javax.net.ssl.trustStore", keyPath);
        System.setProperty("javax.net.ssl.trustStorePassword", keyPassword);
    }

    static MBeanServerConnection createJMXConnection(String serviceURL, String login, String password) throws Exception {
        JMXServiceURL jmxServiceURL = new JMXServiceURL(serviceURL);

        Map<String, String[]> env = new HashMap<>();
        String[] creds = {
                login,
                password
        };
        env.put(JMXConnector.CREDENTIALS, creds);

        JMXConnector jmxConnector = JMXConnectorFactory.connect(jmxServiceURL, env);

        return jmxConnector.getMBeanServerConnection();
    }

    static void dumpOperation(MBeanOperationInfo info) {
        System.out.print(info.getName() + " (");
        System.out.print(Arrays.stream(info.getSignature()).map(p -> p.getType() + " " + p.getName())
                .collect(Collectors.joining(", ")));
        System.out.println(")");
    }


}
