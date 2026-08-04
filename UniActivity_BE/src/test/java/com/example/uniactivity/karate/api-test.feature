Feature: Kiểm thử API UniActivity

  Background:
    * def baseUrl = 'http://localhost:8080'
    * def username = '4551190011'
    * def password = 'Demo123@'
    * url baseUrl

  Scenario: Đăng nhập thành công
    Given path '/api/auth/login'
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    And match response.tokenType == 'Bearer'
    And match response.accessToken == '#string'
    And match response.refreshToken == '#string'
    And match response.expiresIn == '#number'
    And match response.user.username == '#string'
    And match response.user.role == '#string'
    And match response.user.id == '#number'

  Scenario: Đăng nhập sai mật khẩu — 401
    Given path '/api/auth/login'
    And request { username: '#(username)', password: 'sai_mat_khau' }
    When method post
    Then status 401
    And match response.error == '#string'

  Scenario: Đăng nhập thiếu username — 400
    Given path '/api/auth/login'
    And request { username: '', password: '123' }
    When method post
    Then status 400
    And match response.error == '#string'

  Scenario: Refresh token giả — 401
    Given path '/api/auth/refresh'
    And request { refreshToken: 'fake.token.here' }
    When method post
    Then status 401

  Scenario: Đăng nhập rồi refresh token thành công
    Given path '/api/auth/login'
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def refreshToken = response.refreshToken

    Given url baseUrl
    And path '/api/auth/refresh'
    And request { refreshToken: '#(refreshToken)' }
    When method post
    Then status 200
    And match response.accessToken == '#string'
    And match response.tokenType == 'Bearer'

  Scenario: Gọi /api/auth/me không token — 401
    Given path '/api/auth/me'
    When method get
    Then status 401

  Scenario: Gọi /api/auth/me có token — 200
    Given path '/api/auth/login'
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given url baseUrl
    And path '/api/auth/me'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response.username == '#string'
    And match response.id == '#number'
    And match response.role == '#string'


  Scenario: Lấy hồ sơ cá nhân
    Given path '/api/auth/login'
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.accessToken

    Given url baseUrl
    And path '/api/profile'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 200
    And match response.username == '#string'
    And match response.role == '#string'
    And match response.email == '#string'
    And match response.id == '#number'
