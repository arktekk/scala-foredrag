intro

what is directives ?

---

unfiltered

---

extractors

---

naturlig api i scala... bla bla..

---

problemene med pattern matching

* matcher på for mye (success | 404)
* stygt å bruke riktig (har ingen data i 'failure')

---

Intent = PartialFunction[Request, Response]

---

Matcher på for mye

def PathIntent(f:PartialFunction[String, Response]):PartialFunction[Request, Response] = {
	case Path(path) if f.isDefinedAt(path) => f(path)
}

---

men vi trenger tilgang til Request

def PathIntent(f:PartialFunction[String, Request => Response]):PartialFunction[Request, Response] = {
	case req @ Path(path) if f.isDefinedAt(path) => f(path)(req)
}

---

def b(request:Request) = ...

def intent = PathIntent {
	case "/a" => req => ...
	case "/b" => b
}

---

harder to shoot yourself in the foot

* but still ugly

---

Represent failure

```scala
Option[A] = Some[A] | None
```

```scala
sealed trait Result[+A]{
	def map[B](f:A => B):Result[B]
	def flatMap[B](f:A => Result[B]):Result[B]
}
case class Success[A](value:A) extends Result[A]
object Failure(Response) extends Result[Nothing]
```

def PathIntent(f:PF[String, Request => Result[Response]]):PF[Request, Response] = {
	case req @ Path(path) if f.isDefinedAt(path) =>
		f(path)(req) match {
			case Success(response) => response
			case Failure(response) => response
		}
}

---

```scala
def post(request:Request) = request match {
	case POST(_) => Success(())
	case _      => Failure(MethodNotAllowed)
}
```

def example(req:Request) = for {
	_ <- post(req)
	_ <- requestContentType("application/json")(req)
	_ <- accepts(Accepts.Json)(req)
} yield Ok ~> ...

---

now thats an improvement! .. 
but that request is everywhere

---

this conference is named 'flatMap' for a reason ;-)

case class Directive[A](run:Request => Result[A]){
	def map[B](f:A => B) = Directive(r => run(r).map(f))
	def flatMap[B](f:A => Directive[B]) =
		Directive(r => run(r).flatMap(a => f(a).run(r)))
}

def example = for {
	_   <- post
	_   < requestContentType("application/json")
	_   <- accepts(Accepts.Json)
	req <- request
} yield Ok ~> ResponseBytes(Body bytes req)

---

Unfiltered provides extraction of values

Directives.Result deals with 'None' from extraction

Directives composes it all nicely together

---

combinators for simplifying this

implicit def method(M:Method) = when { case M(_) => } orElse MethodNotAllowed

implicit def contentType(R:RequestContentType.type) = when{ case RequestContentType(tpe) => tpe } orElse UnsupportedMediaType

for {
	_ <- POST
	tpe <- RequestContentType
	_ <- if(tpe == "application/json") success(()) else failure(UnsupportedMediaType)
} yield ...

---

support for comparing values from extractors

implicit val requestContentTypeEq = Eq{ (R:RequestContentType.type, value:String) =>
	when { R(`value`) => } orElse UnsupportedMediaType
}

for {
	_ <- POST
	_ <- RequestContenType === "application/json"
} yield ...