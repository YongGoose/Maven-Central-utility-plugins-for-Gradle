# Contributing to Maven Central-utility plugins for Gradle

Thank you for your interest in contributing to the Maven Central utility plugins for Gradle plugin! This document provides guidelines to help make the contribution process smooth and effective.

## Development Environment Setup

1. Fork and clone the repository
2. Ensure you have JDK 17 or later installed
3. Ensure you have Gradle 7.0+ installed

## Building and Testing

```bash
# Run a full build
./gradlew build

# Run tests
./gradlew test
```

## Coding Style

- Follow the Kotlin official coding conventions
- Use ktlint for code style checking: `./gradlew ktlintCheck`
- Format code automatically with ktlint: `./gradlew ktlintFormat`

## Contribution Process

1. Create a branch for your feature or bugfix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
   or
   ```bash
   git checkout -b fix/your-bug-fix
   ```

2. Make your changes and add tests:
    - All new features should include tests
    - Bug fixes should include regression tests where possible

3. Push your changes and create a Pull Request:
    - Clearly describe the changes and purpose in your PR description
    - Reference any related issues

## Pull Request Guidelines

- PR title should be clear and reflect the changes
- Keep PRs focused on a single concern
- Include explanation and context for code changes
- Ensure all CI tests pass

## Questions and Help

If you have questions or need help, please create an issue or reach out through github discussion.

## License

By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.

Thank you for your contributions!
