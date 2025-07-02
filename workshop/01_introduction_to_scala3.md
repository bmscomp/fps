# 1. Introduction to Scala 3

Scala 3 (formerly Dotty) is the latest major revision of the Scala programming language. It brings significant improvements in terms of syntax, type system, and tooling, making Scala even more powerful and enjoyable to use. Scala is a multi-paradigm programming language designed to integrate features of object-oriented and functional programming.

### Key Features and Improvements in Scala 3:

*   **New Syntax**: More readable and less ceremonial syntax, especially for control structures (e.g., `if`, `for`, `while`) and enums.
*   **Enums**: First-class support for enums, which can also be parameterized and have methods.
*   **Intersection and Union Types**: Powerful new type system features for more expressive and flexible type definitions.
*   **Opaque Type Aliases**: Allows creating new types that are type-checked as distinct from their underlying type, but compile to the underlying type, avoiding runtime overhead.
*   **Contextual Abstractions (Implicit Redesign)**: A more principled and explicit way to handle implicits, now called "contextual abstractions," including `given` and `using` clauses.
*   **Extension Methods**: A cleaner way to add methods to existing types without modifying their original definition.
*   **Metaprogramming**: Improved metaprogramming capabilities with a new Macro system.
*   **Trait Parameters**: Traits can now take parameters, making them more flexible and powerful.

### Why Scala 3?

Scala 3 aims to be:

*   **More Expressive**: With new syntax and type system features.
*   **Safer**: Stronger type system helps catch more errors at compile time.
*   **More Performant**: Compiler optimizations and new language features can lead to more efficient code.
*   **Easier to Learn**: Simplified syntax and clearer concepts.

### Functional Programming in Scala

Scala is a hybrid language, but it strongly encourages a functional programming style. Functional programming (FP) is a programming paradigm where programs are constructed by applying and composing functions. It is a declarative programming paradigm, meaning programming is done with expressions or declarations instead of statements.

**Core Principles of FP:**

*   **Immutability**: Data structures are not modified after creation.
*   **Pure Functions**: Functions that, given the same input, will always return the same output and produce no side effects.
*   **First-Class Functions**: Functions can be treated as values (passed as arguments, returned from other functions, assigned to variables).
*   **Higher-Order Functions (HOFs)**: Functions that take other functions as arguments or return functions as results.
