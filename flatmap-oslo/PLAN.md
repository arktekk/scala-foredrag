Unfiltered Directives
=====================
Jon-Anders Teigen

[@jteigen]("http://twitter.com/jteigen")

[Arktekk]("http://arktekk.no")

---

2012
===

Presented an idea @ flatMap(Oslo)

---

2013
====

* Part of Unfiltered 0.6.8
* Used in production

---

Agenda
======
* the problem
* the solution
* the future

---

Why ?
=====
Writing correct and well behaved http applications is hard

---

How hard ?
==========
[rfc 2616](http://www.w3.org/Protocols/rfc2616/rfc2616.html) - 176 pages

LOTS of semantics

---

![http](http-headers-status-v3.png)

---

![http](http-headers-status-v3-zoom.png)

---

~~<span style="color:red">"web frameworks"</span>~~
----------------

server side browser frameworks
------------------------------
* templating
* json
* ajax
* state management
* database access
* information hiding
* ignore most of http

---

I want to be in control!
========================

---

Unfiltered
----------
Unfiltered is a toolkit for servicing HTTP requests in Scala. 
It provides a consistent vocabulary for handing requests on various server backends, 
without impeding direct access to their native interfaces.

---

Unfiltered
----------
![fittings](fittings.jpg)

---

You
---
![Pipewrench](Pipewrench.jpg)


---

Unfiltered is awesome!
----------------------
```scala
def intent = {
  case GET(Path("/example")) => 
    Ok ~> ResponseString("nice..")
}
```

```
(200) curl http://localhost:8080/example
```

---

But can be devious
------------------
```scala
def intent = {
  case GET(Path("/example")) => 
    Ok ~> ResponseString("nice..")
}
```

```
(200) curl http://localhost:8080/example

(404) curl -XPOST http://localhost:8080/example
```

---

Lets fix it
-----------

```scala
def intent = {
  case req @ Path("/example") => req match {
    case GET(_) => Ok ~> ResponseString("nice")
    case _      => MethodNotAllowed	
  }
}
```

```
(200) curl http://localhost:8080/example

(405) curl -XPOST http://localhost:8080/example
```

---

Something more complex
----------------------

```scala
def intent = {
  case req @ Path("/example") => req match {
    case POST(_) => req match {
      case RequestContentType("application/json") => req match {
        case Accepts.Json(_) =>
          Ok ~> JsonContent ~> ResponseBytes(Body.bytes(req))
        case _ => NotAcceptable
      }
      case _ => UnsupportedMediaType
    }
    case _ => MethodNotAllowed
  }
}
```

---

Correct, but not awesome
------------------------

![sad cat](sad-cat.jpg)

---

Do we need correctness ?
------------------------

![dirty](dirt.jpg)

---

What is the problem ?
---------------------
* what we match on
* when it doesn't match

---

Partial functions
----------------
```scala
// you implement
def intent:PartialFunction[Request, Response]

// what unfiltered does
if(intent.isDefinedAt(request)) intent(request) else NotFound
```

---

Correct Behaviour
-------------
```scala
def intent = {
	case req @ Path("/a") => ..
	case req @ Path("/b") => ..
}
```

---

Enforcing it
------------

```scala
def PathIntent(pf:PartialFunction[String, Request => Response])
		:PartialFunction[Request, Response] = {

	case req @ Path(path) if pf.isDefinedAt(path) => pf(path)(req)
}

def intent = PathIntent {
	case "/a" => req => 
	case "/b" => req =>
}
```

---

What is the problem ?
---------------------
* ~~<span style="color:green">what we match on</span>~~
* when it doesn't match

---

Failure is not an option
------------------------
```scala
object GET {
  def unapply(req:Request) = 
    if(req.method.equalsIgnoreCase("get")) Some(req) else None
}

req match {
  case GET(_) => ..
  case _      => MethodNotAllowed
}
```

---

a better failure
----------------
```scala
sealed trait Result[+A]{
  def map[B](f:A => B):Result[B]
  def flatMap[B](f:A => Result[B]):Result[B]
}
case class Success[A](value:A) extends Result[A]
case class Failure(response:Response) extends Result[Nothing]
```

---

failing properly
----------------
```scala
def post(req:Request) = req match {
  case POST(_) => Success(())
  case _       => Failure(MethodNotAllowed)
}

def contentType(tpe:String)(req:Request) = req match {
  case RequestContentType(`tpe`) => Success(())
  case _                         => Failure(UnsupportedMediaType)
}
```

---

```scala
def PathIntent(pf:PartialFunction[String, Request => Result[Response]]) = {
  case req @ Path(path) if pf.isDefinedAt(path) =>
    pf(path)(req) match {
	  case Success(response) => response
	  case Failure(response) => response
    }
}

def intent = PathIntent {
  case "/example" => req =>
    for {
      _ <- post(req)
      _ <- contentType("application/json")(req)
    } yield Ok ~> ...
}
```

---

Directives
----------
```scala
case class Directive[A](run:Request => Result[A]) 
    extends (Request => Result[A]){

  def apply(req:Request) = run(req)

  def map[A](f:A => B):Directive[B] = 
    Directive(r => run(r).map(f))

  def flatMap[B](f:A => Directive[B]):Directive[B] = 
    Directive(r => run(r).flatMap(a => f(a)(r)))
}
```

---

Reuse Unfiltered
----------------
```scala
implicit def method(M:Method) = Directive {
  case M(_) => Success(())
  case _    => Failure(MethodNotAllowed)
}

implicit val contentType = Directive.Eq { 
  (R:RequestContentType.type, value:String) =>
    when{ case R(`value`) => } orElse UnsupportedMediaType
}
```

---

Remember this one ?
-------------------
```scala
def intent = {
  case req @ Path("/example") => req match {
    case POST(_) => req match {
      case RequestContentType("application/json") => req match {
        case Accepts.Json(_) =>
          Ok ~> JsonContent ~> ResponseBytes(Body.bytes(req))
        case _ => NotAcceptable
      }
      case _ => UnsupportedMediaType
    }
    case _ => MethodNotAllowed
  }
}
```

---

Awesomeness restored
--------------------
```scala
def intent = Path.Intent {
  case "/example" => for {
    _ <- POST
    _ <- RequestContentType === "application/json"
    _ <- Accepts.Json
    req <- request[Any]
  } yield Ok ~> JsonContent ~> ResponseBytes(Body bytes req)
}
```

---

If at first you don't succeed
-----------------------------
```scala
val a = for {
	_ <- GET
	_ <- failure(BadRequest)
} yield Ok ~> ResponseString("a")

val b = for {
	_ <- POST
} yield Ok ~> ResponseString("b")

def intent = Path.Intent { 
  case "/x" =>  a | b 
}
```

---

can you spot the problem ?
--------------------------
```scala
val a = for {
	_ <- GET
	_ <- failure(BadRequest)
} yield Ok ~> ResponseString("a")

val b = for {
	_ <- POST
} yield Ok ~> ResponseString("b")

def intent = Path.Intent { 
  case "/x" =>  a | b 
}
```
```
curl http://localhost/x

? 400 Bad Request
? 405 Method Not Allowed
? 200 Ok ("a")
? 200 OK ("b")
```

---

commitment!
-----------
```scala
val a = for {
  _ <- GET
  _ <- commit
  _ <- failure(BadRequest)
} yield Ok ~> ResponseString("a")

val b = for {
  _ <- POST
} yield Ok ~> ResponseString("b")
```
```
curl http://localhost:8080/x
400 Bad Request
```

---

how does commit work ?
----------------------
```scala
// 3 types
Result[A] = Success(a) | Failure(response) | Error(response)

// rewrites
commit(Success) == Success
commit(Failure) == Error
commit(Error)   == Error

// combines
Success | X == Success
Failure | X == X
Error   | X == Error
```

---

Improving Failure
------------------
```scala
for {
  _ <- POST.fail ~> ResponseString("I pity the fool who can't POST!")
} yield ...
```
```
curl -XGET http://...

405 I pity the fool who can't POST!
```

---

The future
----------
* directives for Async Plans
* directives for all/more of the unfiltered extractors
* Eq/Lt/Gt instances for unfiltered extractors
* richer directives providing correct content negotiation etc.

---

Thank you
---------

---

legal
------
* http://www.catversushuman.com/2013/04/my-cat-money-when-i-let-him-outside.html
