# Lessons from Issue #78

## Pattern: ControllerAdvice with optional dependencies

When creating a `@ControllerAdvice` that may be used in narrow test contexts (e.g., `@WebMvcTest`), use `ObjectProvider<T>` instead of direct injection. This allows graceful degradation when the dependency is not available.

**Correct:**
```java
@ControllerAdvice
public class MyAdvice {
    private final ObjectProvider<OptionalService> serviceProvider;
    
    public MyAdvice(ObjectProvider<OptionalService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }
    
    @ModelAttribute("attr")
    public Boolean hasSomething() {
        OptionalService service = serviceProvider.getIfAvailable();
        if (service == null) {
            return false; // fail closed
        }
        // ... use service
    }
}
```

**Incorrect:**
```java
@ControllerAdvice
public class MyAdvice {
    private final OptionalService service; // breaks narrow test contexts
    
    public MyAdvice(OptionalService service) {
        this.service = service;
    }
}
```

## Pattern: Test assertions should match actual output format

When writing assertions for HTML content, check for substrings that exist in the actual rendered output, not exact string matches that might differ due to:
- Additional attributes added by templates
- Whitespace differences
- CSS class ordering

Instead of:
```java
assertThat(html).contains("<div class=\"foo\">");
```

Use:
```java
assertThat(html).contains("class=\"foo\"");
// or
assertThat(html).contains("foo");
```
