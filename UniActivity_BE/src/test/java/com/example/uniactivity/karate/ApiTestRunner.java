package com.example.uniactivity.karate;

import com.intuit.karate.junit5.Karate;

/**
 * Karate Test Runner — chạy tất cả file .feature trong package karate.
 * 
 * Cách chạy:
 *   mvn test -Dtest=ApiTestRunner
 * 
 * Báo cáo HTML tự động xuất ra:
 *   target/karate-reports/karate-summary.html
 */
class ApiTestRunner {

    @Karate.Test
    Karate testApi() {
        // Chạy duy nhất file api-test.feature trong cùng package
        return Karate.run("api-test").relativeTo(getClass());
    }
}
