# Reqour

[Repour](https://github.com/project-ncl/repour), but written in Quarkus.

# Testing

In order to allow more fine-grained control over which tests are being run, test profiles defining tags are used. In
case you want to run only translation-related tests, run:
```bash
mvn test -Dquarkus.test.profile.tags=translation
```
In case you want to run all the tests, simply do not specify `quarkus.test.profile.tags` system property at all.

For more information, see [Quarkus testing guide](https://quarkus.io/guides/getting-started-testing#testing_different_profiles).
