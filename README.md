# `nacos-property-spring-boot-refresher`

A dynamic refresher for Spring Boot property beans powered by `Nacos`.

[Examples](https://github.com/photowey/nacos-property-refresher-spring-boot-starter-examples)

## 1.`Usage`

Add this to your `pom.xml`

```xml
<!-- ${nacos-property-refresher-starter.version} == ${latest.version} -->
<dependency>
    <groupId>io.github.photowey</groupId>
    <artifactId>nacos-property-refresher-spring-boot-starter</artifactId>
    <version>${nacos-property-refresher-starter.version}</version>
    <type>pom</type>
</dependency>
```

## 2.`APIs`

### 2.1.`Nacos`

- `data-ids`
  - `${spring.application.name}`
  - `${spring.application.name}-dev`
  - `${spring.application.name}-app`
    - monitored data-id

#### 2.1.1.`${spring.application.name}`

- Configure as you like

```yml
local:
  config:
    database:
      postgresql:
        host: 192.168.1.11
        post: 5432
        username: root
        password: root
        database: hello
```



#### 2.1.2.`${spring.application.name}-dev`

```yml
server:
  port: 9527

io:
  github:
    photowey:
      static:
        property:
          cache:
            loader: mongo
            expired: 10
            unit: MINUTES
```



#### 2.1.3.`${spring.application.name}-app`

- See `HelloDynamicNacosConfigListener#DYNAMIC_DATA_IDS`

```yml
io:
  github:
    photowey:
      dynamic:
        property:
          cache:
            loader: database
            expired: 10
            unit: MINUTES
```



### 2.1.`Annotation`

#### 2.1.0.`Properties`

```java
@Data
//@ConfigurationProperties(prefix = "io.github.photowey.dynamic.property")
public class AppProperties {

    private Cache cache = new Cache();

    @Data
    public static class Cache implements Serializable {

        // ${io.github.photowey.dynamic.property.cache.loader}
        private String loader = "local";
        // ${io.github.photowey.dynamic.property.cache.expired}
        private long expired = TimeUnit.MINUTES.toMillis(5);
        // ${io.github.photowey.dynamic.property.cache.unit}
        private TimeUnit unit = TimeUnit.MILLISECONDS;
    }

    public static String getPrefix() {
        return "io.github.photowey.dynamic.property";
    }

}
```

```java
@Data
//@ConfigurationProperties(prefix = "io.github.photowey.static.property")
public class HelloProperties {

    private Cache cache = new Cache();

    @Data
    public static class Cache implements Serializable {

        // ${io.github.photowey.static.property.cache.loader}
        private String loader = "local";
        // ${io.github.photowey.static.property.cache.expired}
        private long expired = TimeUnit.MINUTES.toMillis(5);
        // ${io.github.photowey.static.property.cache.unit}
        private TimeUnit unit = TimeUnit.MILLISECONDS;
    }

    public static String getPrefix() {
        return "io.github.photowey.static.property";
    }

}
```



#### 2.1.1.`Configuration`

```java
@Configuration
public class DynamicPropertyConfigure {

	// ...
    
    @Bean
    @NacosDynamicRefreshScope
    public AppProperties appProperties(Environment environment) {
        return PropertyBinders.bind(environment, AppProperties.getPrefix(), AppProperties.class);
    }

    @Bean
    @NacosDynamicRefreshScope
    public HelloProperties helloProperties(Environment environment) {
        return PropertyBinders.bind(environment, HelloProperties.getPrefix(), HelloProperties.class);
    }
    
    // ...
}
```



#### 2.1.2.`Beans`

> `@NacosDynamicRefreshScope`

```java
@RestController
@RequestMapping("/api/v1/scope")
@NacosDynamicRefreshScope
public class ScopeApiController {

}
```



### 2.2.`Listener`

#### 2.2.1.`Refresh`

```java
// @Component || @Bean
public class HelloDynamicNacosConfigListener extends AbstractNacosDynamicRefreshListener {

    // {} -> ${spring.application.name}
    // Register the dataid list that needs to be refreshed dynamically. 
    private static final List<String> DYNAMIC_DATA_IDS = Lists.newArrayList(
            "{}-app"
    );

    @Override
    public void registerListener(Collection<ConfigService> configServices) {
        for (ConfigService configService : configServices) {
            DYNAMIC_DATA_IDS.forEach(dataIdTemplate -> this.addTemplateListener(configService, dataIdTemplate));
        }
    }
    
    // ...this#addListener
}

```



### 2.3.`Controllers`

#### 2.3.1.`Normal`

```java
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    public static String DYNAMIC_KEY = "io.github.photowey.dynamic.property.cache.loader";
    public static String STATIC_KEY = "io.github.photowey.static.property.cache.loader";

    @Autowired
    private Environment environment;

    @Value("${io.github.photowey.dynamic.property.cache.loader}")
    private String dynamicLoader;

    @Value("${io.github.photowey.static.property.cache.loader}")
    private String staticLoader;

    @Autowired
    private AppProperties appProperties;
    @Autowired
    private HelloProperties helloProperties;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * http://localhost:9527/api/v1/static/get/dataid/dev
     *
     * @return {@link DynamicValuesDTO}
     */
    @GetMapping("/static/get/dataid/dev")
    public ApiResult<DynamicValuesDTO> dev() {
        DynamicValuesDTO dto = DynamicValuesDTO.builder()
                .value(this.staticLoader)
                .environment(this.tryAcquireLoaderFromEnvironment(STATIC_KEY))
                .property(this.helloProperties.getCache().getLoader())
                .ctxProperty(this.applicationContext.getBean(HelloProperties.class).getCache().getLoader())
                .build();

        return ApiResult.ok(dto);
    }

    /**
     * http://localhost:9527/api/v1/dynamic/get/dataid/app
     *
     * @return {@link DynamicValuesDTO}
     */
    @GetMapping("/dynamic/get/dataid/app")
    public ApiResult<DynamicValuesDTO> app() {
        DynamicValuesDTO dto = DynamicValuesDTO.builder()
                .value(this.dynamicLoader)
                .environment(this.tryAcquireLoaderFromEnvironment(DYNAMIC_KEY))
                .property(this.appProperties.getCache().getLoader())
                .ctxProperty(this.applicationContext.getBean(AppProperties.class).getCache().getLoader())
                .build();

        return ApiResult.ok(dto);
    }

    private String tryAcquireLoaderFromEnvironment(String key) {
        return this.environment.getProperty(key);
    }
}
```



#### 2.3.2.`Scope`

```java
@RestController
@RequestMapping("/api/v1/scope")
@NacosDynamicRefreshScope
public class ScopeApiController {

    public static String DYNAMIC_KEY = "io.github.photowey.dynamic.property.cache.loader";
    public static String STATIC_KEY = "io.github.photowey.static.property.cache.loader";

    @Autowired
    private Environment environment;

    @Value("${io.github.photowey.dynamic.property.cache.loader}")
    private String dynamicLoader;

    @Value("${io.github.photowey.static.property.cache.loader}")
    private String staticLoader;

    @Autowired
    private AppProperties appProperties;
    @Autowired
    private HelloProperties helloProperties;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * http://localhost:9527/api/v1/scope/static/get/dataid/dev
     *
     * @return {@link DynamicValuesDTO}
     */
    @GetMapping("/static/get/dataid/dev")
    public ApiResult<DynamicValuesDTO> dev() {
        DynamicValuesDTO dto = DynamicValuesDTO.builder()
                .value(this.staticLoader)
                .environment(this.tryAcquireLoaderFromEnvironment(STATIC_KEY))
                .property(this.helloProperties.getCache().getLoader())
                .ctxProperty(this.applicationContext.getBean(HelloProperties.class).getCache().getLoader())
                .build();

        return ApiResult.ok(dto);
    }

    /**
     * http://localhost:9527/api/v1/scope/dynamic/get/dataid/app
     *
     * @return {@link DynamicValuesDTO}
     */
    @GetMapping("/dynamic/get/dataid/app")
    public ApiResult<DynamicValuesDTO> app() {
        DynamicValuesDTO dto = DynamicValuesDTO.builder()
                .value(this.dynamicLoader)
                .environment(this.tryAcquireLoaderFromEnvironment(DYNAMIC_KEY))
                .property(this.appProperties.getCache().getLoader())
                .ctxProperty(this.applicationContext.getBean(AppProperties.class).getCache().getLoader())
                .build();

        return ApiResult.ok(dto);
    }

    private String tryAcquireLoaderFromEnvironment(String key) {
        return this.environment.getProperty(key);
    }
}
```



#### 2.3.3.`Reulst`

> `redis` -> `database`

```json
{
  "code": "200",
  "message": "OK",
  "data": {
    "value": "redis",
    "environment": "database",
    "property": "redis",
    "ctxProperty": "database"
  }
}
```

```json
{
  "code": "200",
  "message": "OK",
  "data": {
    "value": "database",
    "environment": "database",
    "property": "database",
    "ctxProperty": "database"
  }
}
```

```json
{
  "code": "200",
  "message": "OK",
  "data": {
    "value": "...",
    "environment": "...",
    "property": "...",
    "ctxProperty": "..."
  }
}
```



