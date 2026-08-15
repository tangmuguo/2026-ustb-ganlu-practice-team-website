package com.vihu.ganlu.audit;

/** Per-request metadata used by the append-only audit service. */
public final class AuditRequestContext {
    private static final ThreadLocal<Values> VALUES = new ThreadLocal<Values>();

    private AuditRequestContext() { }

    public static void set(String requestId, String sourceIp, String method, String path,
                           String targetHost, Integer targetPort, String userAgent) {
        VALUES.set(new Values(requestId, sourceIp, method, path, targetHost, targetPort, userAgent));
    }

    public static Values get() { return VALUES.get(); }

    public static void clear() { VALUES.remove(); }

    public static final class Values {
        private final String requestId;
        private final String sourceIp;
        private final String method;
        private final String path;
        private final String targetHost;
        private final Integer targetPort;
        private final String userAgent;

        Values(String requestId, String sourceIp, String method, String path,
               String targetHost, Integer targetPort, String userAgent) {
            this.requestId = requestId;
            this.sourceIp = sourceIp;
            this.method = method;
            this.path = path;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.userAgent = userAgent;
        }

        public String getRequestId() { return requestId; }
        public String getSourceIp() { return sourceIp; }
        public String getMethod() { return method; }
        public String getPath() { return path; }
        public String getTargetHost() { return targetHost; }
        public Integer getTargetPort() { return targetPort; }
        public String getUserAgent() { return userAgent; }
    }
}
