package integration;

final class Subject {
    private final Dependency dependency = new Dependency();

    Dependency dependency() {
        return dependency;
    }
}
