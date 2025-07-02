# 2. Functional Programming Refresher

Before diving deep into ZIO, let's quickly recap some fundamental functional programming concepts that are crucial for understanding ZIO.

### Immutability

In functional programming, data is immutable. Once a data structure is created, it cannot be changed. Instead of modifying existing data, you create new data structures with the desired changes.

**Example (Scala):**

```scala
val originalList = List(1, 2, 3)
val newList = originalList :+ 4 // Creates a new list (1, 2, 3, 4), originalList remains unchanged

println(s"Original List: $originalList")
println(s"New List: $newList")
```

### Pure Functions

A pure function is a function that satisfies two conditions:

1.  **Deterministic**: Given the same input, it always returns the same output.
2.  **No Side Effects**: It does not cause any observable changes outside its local environment (e.g., modifying mutable data, performing I/O, throwing exceptions).

**Example (Scala):**

```scala
def add(a: Int, b: Int): Int = a + b // Pure function

var counter = 0
def incrementAndGet(): Int = { // Impure function (modifies external state)
  counter += 1
  counter
}

println(s"Add: ${add(2, 3)}")
println(s"Increment 1: ${incrementAndGet()}")
println(s"Increment 2: ${incrementAndGet()}")
```

### Higher-Order Functions (HOFs)

HOFs are functions that either take one or more functions as arguments or return a function as their result. They are a powerful abstraction mechanism in functional programming.

**Common HOFs in Scala Collections:**

*   `map`: Transforms each element of a collection.
*   `filter`: Selects elements based on a predicate.
*   `fold`/`reduce`: Combines elements into a single result.

**Example (Scala):**

```scala
val numbers = List(1, 2, 3, 4, 5)

// Using map to double each number
val doubledNumbers = numbers.map(x => x * 2)
println(s"Doubled Numbers: $doubledNumbers")

// Using filter to get even numbers
val evenNumbers = numbers.filter(_ % 2 == 0)
println(s"Even Numbers: $evenNumbers")

// Using foldLeft to sum numbers
val sum = numbers.foldLeft(0)(_ + _)
println(s"Sum: $sum")
```

### Type Classes

Type classes are a powerful concept in functional programming that allows you to add new behavior to existing types without modifying their original definition. They are a way to achieve ad-hoc polymorphism.

In Scala, type classes are typically implemented using traits and implicit (now `given`/`using`) parameters.

**Example (Scala - simplified `Show` type class):**

```scala
trait Show[A] {
  def show(value: A): String
}

object Show {
  def apply[A](implicit s: Show[A]): Show[A] = s

  implicit val intShow: Show[Int] = new Show[Int] {
    def show(value: Int): String = s"Int($value)"
  }

  implicit val stringShow: Show[String] = new Show[String] {
    def show(value: String): String = s"String(\"$value\")"
  }
}

// Extension method to make it more ergonomic
extension class ShowOps[A](value: A)(using s: Show[A]) {
  def show: String = s.show(value)
}

println(123.show)
println("hello".show)
```
