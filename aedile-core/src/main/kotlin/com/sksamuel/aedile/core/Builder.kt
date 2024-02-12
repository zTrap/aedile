package com.sksamuel.aedile.core

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture

class Builder<K, V>(
   private val defaultScope: CoroutineScope,
   private val useCallingContext: Boolean,
   private val caffeine: Caffeine<Any, Any>,
) {

   /**
    * Returns a [CacheFacade] which suspends when requesting values.
    *
    * If the key is not present in the cache, returns null, unless a compute function
    * is provided with the key.
    *
    * If the suspendable computation throws or computes a null value then the
    * entry will be automatically removed.
    */
   fun build(): CacheFacade<K, V> {
      return CacheFacadeImpl(defaultScope, useCallingContext, caffeine.buildAsync())
   }

   /**
    * Returns a [LoadingCacheFacade] which suspends when requesting values.
    *
    * If the key does not exist, then the suspendable [compute] function is invoked
    * to compute a value, unless a specific compute has been provided with the key.
    *
    * If the suspendable computation throws or computes a null value then the
    * entry will be automatically removed.
    *
    */
   fun build(compute: suspend (K) -> V): LoadingCacheFacade<K, V> {
      return LoadingCacheFacadeImpl(
         defaultScope,
         useCallingContext,
         caffeine.buildAsync { key, _ -> defaultScope.async { compute(key) }.asCompletableFuture() }
      )
   }

   /**
    * Returns a [LoadingCacheFacade] which suspends when requesting values.
    *
    * If the key does not exist, then the suspendable [compute] function is invoked
    * to compute a value, unless a specific compute has been provided with the key.
    *
    * If the suspendable computation throws or computes a null value then the
    * entry will be automatically removed.
    *
    * The [reloadCompute] function is invoked to refresh an entry if refreshAfterWrite
    * is enabled or refresh is invoked. See full docs [AsyncCacheLoader.asyncReload].
    *
    */
   fun build(compute: suspend (K) -> V, reloadCompute: suspend (K, V) -> V): LoadingCacheFacade<K, V> {
      return LoadingCacheFacadeImpl(
         defaultScope,
         useCallingContext,
         caffeine.buildAsync(object : AsyncCacheLoader<K, V> {
            override fun asyncLoad(key: K, executor: Executor?): CompletableFuture<out V> {
               return defaultScope.async { compute(key) }.asCompletableFuture()
            }

            override fun asyncReload(key: K, oldValue: V, executor: Executor?): CompletableFuture<out V> {
               return defaultScope.async { reloadCompute(key, oldValue) }.asCompletableFuture()
            }
         })
      )
   }

   /**
    * Returns a [LoadingCacheFacade] which suspends when requesting values.
    *
    * If a requested key does not exist, then the suspendable [compute] function is invoked
    * to compute the required values.
    *
    * If the suspendable computation throws or computes a null value then the
    * entry will be automatically removed.
    *
    */
   fun buildAll(compute: suspend (Set<K>) -> Map<K, V>): LoadingCacheFacade<K, V> {
      return LoadingCacheFacadeImpl(
         defaultScope,
         useCallingContext,
         caffeine.buildAsync(AsyncCacheLoader.bulk { keys, _ ->
            defaultScope.async { compute(keys) }.asCompletableFuture()
         })
      )
   }
}
