package com.selfdualbrain.util

import scala.annotation.tailrec

object LanguageTweaks {

  implicit class RichIterator[A](iterator: Iterator[A]) {

    def last: Option[A] = {

      @tailrec
      def loop(lastSeen: A): Option[A] =
        if (iterator.hasNext)
          loop(iterator.next())
        else
          Some(lastSeen)

      if (iterator.isEmpty)
        None
      else
        loop(iterator.next())

    }
  }

}
