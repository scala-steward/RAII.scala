package com.thoughtworks.deeplearning

import language.existentials
import language.implicitConversions
import language.higherKinds
import scala.annotation.elidable

object Layer {

  private[deeplearning] trait CloseableOnce extends AutoCloseable {

    private[CloseableOnce] final class ClosingFlag {
      var closed = false
      @elidable(elidable.ASSERTION)
      def close() = {
        assert(!closed)
        closed = true
      }

      @elidable(elidable.ASSERTION)
      def assertClosed() = {
        assert(closed)
      }
    }

    // FIXME: @elidable should only be used for def
    @elidable(elidable.ASSERTION)
    private val closingFlag = new ClosingFlag

    override def close() = {
      closingFlag.close()
    }

    override protected def finalize(): Unit = {
      closingFlag.assertClosed()
    }
  }

  object Batch {

    /** @template */
    type Aux[+Data0, -Delta0] = Batch {
      type Data <: Data0
      type Delta >: Delta0
    }

  }

  /**
    * Batch是对Data和Delta的封装，每个Batch都包含`backward()`,详细信息可参看[[Layer]]
    */
  trait Batch extends AutoCloseable {
    type Data
    type Delta

    def addReference(): Batch.Aux[Data, Delta]

    protected def forceBackward(delta: Delta): Unit

    def isTrainable: Boolean

    @inline
    final def backward(delta: => Delta): Unit = {
      if (isTrainable) {
        forceBackward(delta)
      }
    }

    def value: Data
  }

  /** @template */
  type Aux[-Input0 <: Batch, +Output0 <: Batch] =
    Layer {
      type Input >: Input0
      type Output <: Output0
    }

}

/**
  * Layer包括Input，Output和forward，Input和Output都是[[com.thoughtworks.deeplearning.Layer.Batch]],
  * 而Batch包含[[com.thoughtworks.deeplearning.Layer.Batch.backward()]],所以Layer所组成的网络会包含输入和输出，正向传播和反向传播。
  *
  * @example{{{
  *  val depthKernelKernel: Layer.Aux[Input, Batch.Aux[Int, Float]] =
  *    Times(
  *       Times(depth, Literal(kernel._1)),
  *       Literal(kernel._2)
  *     )
  *  val bSeq: Seq[Layer.Aux[Input, Batch.Aux[Int, Float]]] = Seq(kernelNumber, depthKernelKernel)
  *  val reshapeWeightTo: Layer.Aux[Input, Batch.Aux[Seq[Int], (Int, Float)]] = DifferentiableSeq.Layers.ToSeq(bSeq)
  *  val reshapedWeight = Reshape(weight, reshapeWeightTo)
  * }}}
  *
  * 以上代码等价于weight.reshape(kernelNumber,depth * KernelSize * KernelSize),
  * 在DeepLearning.scala中，`Reshape()`和`reshape()`其实是等价的(可以参考[[com.thoughtworks.deeplearning.DifferentiableINDArray#reshape]]的具体实现),
  * `reshape()`只是一个语法糖，其实最终还是调用`Reshape()`，调用`reshape()`会产生一个''case class''，而示例中的多个方法嵌套调用会生成类似这样的树：
  * Reshape(Weight([1,2,3]),ToSeq(Times(Times(kernel._1,kernel._2),depth)))，然后forward就从最里面的Times()开始，直到最外面的Reshape(),
  * 然后backward从Reshape()开始，直到最里面的Times()结束。
  */
trait Layer {

  import Layer._

  type Input <: Batch

  type Output <: Batch

  def forward(input: Input): Output

}
