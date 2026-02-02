# Contributing to TideWatch

Thank you for your interest in contributing to TideWatch! This document provides guidelines for contributing to the project.

## Code of Conduct

Be respectful and constructive. We're building a tool for the maritime community.

## How to Contribute

### Reporting Bugs

Use GitHub Issues to report bugs. Include:
- Description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Device info (watch model, WearOS version)
- Screenshots if applicable

### Suggesting Features

Open an issue with the "enhancement" label. Describe:
- The feature and its use case
- Why it would be valuable
- Any implementation ideas

### Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Write or update tests
5. Ensure all tests pass (`./gradlew test`)
6. Commit with clear messages
7. Push to your fork
8. Open a Pull Request

### Development Guidelines

**Code Style:**
- Follow Kotlin coding conventions
- Use meaningful variable names
- Add comments for complex algorithms
- Keep functions focused and small

**Testing:**
- Write unit tests for new calculations
- Validate against NOAA predictions
- Test on physical WearOS device when possible

**Commits:**
- Use clear, descriptive commit messages
- Reference issue numbers when applicable
- Keep commits atomic and focused

### Areas for Contribution

**High Priority:**
- UI/UX improvements for small screens
- Battery optimization
- Test coverage
- Documentation

**Medium Priority:**
- International stations (non-NOAA)
- Tidal currents
- Watch face complications
- Multi-language support

**Nice to Have:**
- Advanced features (moon phase, fishing times)
- Data export
- Offline maps

## Development Setup

See [README.md](README.md#build-instructions) for setup instructions.

### Running Tests

```bash
# Unit tests
./gradlew test

# Lint
./gradlew lint

# All checks
./gradlew check
```

### Data Pipeline

To work with the data pipeline:

```bash
cd tools/data-pipeline
pip install -r requirements.txt
python fetch_noaa_data.py
python build_database.py
```

## Questions?

Open an issue or discussion on GitHub. We're happy to help!

## License

By contributing, you agree that your contributions will be licensed under GPL-3.0.
