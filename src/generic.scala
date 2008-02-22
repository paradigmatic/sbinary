/**
 * Generic operations for building binary instances.
 */
package sbinary.generic;
import sbinary.Operations._;
import scala.collection.mutable._;

import java.io._;


object Building{
  trait Buildable[I[_]]{
    def builder[T] () : Builder[T]
    def builderOfCapacity[T](n : Int) : Builder[T] = builder[T] ();

    trait Builder[T]{
      def += (t : T) : Unit;
      def ++= (ts : T*) = for (t <- ts) { this += t }
      def build : I[T];
    }
  }

  implicit object ListIsBuildable extends Buildable[List]{
    def builder[T] () = new Builder[T]{
      val buffer = new ListBuffer[T]();
      def += (t : T) = buffer += t;
      def build = buffer.toList;
    } 
  }

  implicit object ArrayIsBuildable extends Buildable[Array]{
    class ArrayBuilder[T](buffer : ArrayBuffer[T]) extends Builder[T]{
      def += (t : T) = (buffer += t);
      def build = buffer.toArray;     
    }

    def builder[T] () = new ArrayBuilder[T](new ArrayBuffer[T])
    override def builderOfCapacity[T](n : Int) = new ArrayBuilder[T](new ArrayBuffer[T]{
      ensureSize(n);
    });
  }
}

object Generic {
  import Building._;
  import Instances._;
  def lengthEncoded[S, T[R] <: Collection[R]](implicit bin : Binary[S], build : Buildable[T]) = new Binary[T[S]]{
    def reads(stream : DataInput) : T[S] = {
      val length = read[Int](stream);
      readMany[S, T](length)(stream);
    }

    def writes(ts : T[S])(stream : DataOutput) = {
      write(ts.size)(stream);
      ts.foreach(write[S](_ : S)(stream));
    }
  }

  def readMany[S, I[_]](length : Int)(stream : DataInput)(implicit bin : Binary[S], build : Buildable[I]) : I[S] = {
      val buffer = build.builderOfCapacity[S](length);
      for (i <-  0 until length){
        buffer += read[S](stream);
      }
      buffer.build;
  } 

  def asSingleton[T](t : T) : Binary[T] = new Binary[T]{
    def reads(stream : DataInput) = t
    def writes(t : T)(stream : DataOutput) = ();
  }

  <#list 1..9 as i> 
  <#assign typeParams><#list 1..i as j>T${j}<#if i !=j>,</#if></#list></#assign>
  def asProduct${i}[S, ${typeParams}](apply : (${typeParams}) => S)(unapply : S => Product${i}[${typeParams}])(implicit
   <#list 1..i as j>
      bin${j} : Binary[T${j}] <#if i != j>,</#if>
    </#list>) = new Binary[S]{
       def reads (input : DataInput) : S = apply(
      <#list 1..i as j>
         read[T${j}](input)<#if i != j>,</#if>
      </#list>
      )

      def writes(s : S)(output : DataOutput) = {
        val product = unapply(s);
        <#list 1..i as j>
          write(product._${j})(output);
        </#list>;       
      }
    }  
</#list>

<#list 2..9 as i>
  def asUnion${i}[S, <#list 1..i as j>T${j} <: S<#if i !=j>,</#if></#list>](fold : (
    <#list 1..i as j>
      T${j} => Unit <#if i!=j>,<#else>) => (S => Unit)</#if>       
    </#list>
  ) (implicit 
    <#list 1..i as j>
      bin${j} : Binary[T${j}] <#if i!=j>,</#if>       
    </#list>
  ) : Binary[S] = new Binary[S]{
      def reads (stream : DataInput) = 
        read[Byte](stream) match {
          <#list 1..i as j>
            case ${j} => read[T${j}](stream)
          </#list>
        }

      def writes (s : S)(stream : DataOutput) = 
        fold(
          <#list 1..i as j>
            (x : T${j}) => { write[Byte](${j})(stream); write[T${j}](x)(stream) }<#if i!=j>,</#if>
          </#list>
        )(s)
    } 
</#list>

}