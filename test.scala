case class Person(name:String, age:Int)

val people = List(Person("kid-a", 10), 
                  Person("kid-b", 12), 
                  Person("mom", 42), 
                  Person("dad", 43))
                  
val (kids, adults) = people.partition(_.age < 18)
                  
val ages = people.map(_.age)

val totAge = ages.sum

var totalAge = 0
for(person <- people){
  totalAge += person.age
}

val young = people.filter(_.age < 18)

case class Owner(pets:List[String])

val family = List(Owner(List("Dog", "Cat")), 
                  Owner(List("Fish")))

val familyPets = family.flatMap(p => p.pets)
// List("Dog", "Cat", "Fish")



println(kids + " : " + adults)