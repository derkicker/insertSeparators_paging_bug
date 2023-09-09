package com.example.pagingtest

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.insertSeparators
import androidx.paging.map
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.Main

    private lateinit var db: AppDatabase

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "database-name"
        ).build()

        launch(Dispatchers.IO) {
            db.itemDao().deleteAll()
        }

        setContent {
            val pagingItems = remember {
                getPager().flow
                    .mapLatest {
                        it
                            .map { item -> UiItem.TextItem(item.value) }
                            .insertSeparators { first: UiItem.TextItem?, second: UiItem.TextItem? ->
                                when {
                                    first == null -> null
                                    second == null -> null
                                    second.value % 10 == 0 -> UiItem.SeparatorItem("${first.value / 10}x")
                                    else -> null
                                }
                            }
                    }
                    .flowOn(Dispatchers.IO)
            }.collectAsLazyPagingItems()
            
            LazyColumn(
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                Log.d("Compose", "LazyColumn: ${pagingItems.itemCount}")

                items(
                    count = pagingItems.itemCount,
                    key = { index ->
                        when (val item = pagingItems.peek(index)) {
                            is UiItem.TextItem -> item.value
                            is UiItem.SeparatorItem -> item.text
                            else -> -index - 100
                        }
                    },
                ) { index ->
                    pagingItems[index]?.let { item ->
                        when (item) {
                            is UiItem.TextItem -> {
                                Item(
                                    item = item,
                                    onClick = { }
                                )
                            }
                            is UiItem.SeparatorItem -> {
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(5.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    } ?: run {
                        Text(
                            text = "Placeholder #$index",
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color.Yellow)
                                .padding(horizontal = 5.dp, vertical = 30.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalPagingApi::class)
    private fun getPager() = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = true,
            prefetchDistance = 5,
            initialLoadSize = 20,
        ),
        remoteMediator = object : RemoteMediator<Int, Item>() {
            override suspend fun load(
                loadType: LoadType,
                state: PagingState<Int, Item>
            ): MediatorResult {
                return when (loadType) {
                    LoadType.REFRESH -> {
                        val key = state.anchorPosition ?: 0
                        val items = (key * state.config.initialLoadSize until (key + 1) * state.config.initialLoadSize).map { Item(it) }
                        db.itemDao().insert(items)

                        MediatorResult.Success(false)
                    }
                    LoadType.APPEND -> {
                        val page = state.lastItemOrNull()?.let {
                            it.value / state.config.pageSize + 1
                        } ?: 0

                        val items = (page * state.config.pageSize until (page + 1) * state.config.pageSize).map { Item(it) }
                        db.itemDao().insert(items)

                        MediatorResult.Success(false)
                    }
                    LoadType.PREPEND -> {
                        MediatorResult.Success(true)
                    }
                }
            }
        },
        pagingSourceFactory = { db.itemDao().getSource() }
    )

    sealed class UiItem {
        class TextItem(val value: Int) : UiItem()
        class SeparatorItem(val text: String) : UiItem()
    }
}

@Composable
fun Item(item: MainActivity.UiItem.TextItem, onClick: (Int) -> Unit) {
    Log.d("Compose", "Item: ${item.value}")
    Text(
        text = item.value.toString(),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clickable {
                onClick(item.value)
            }
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 5.dp, vertical = 30.dp)
            .fillMaxWidth()
    )
}