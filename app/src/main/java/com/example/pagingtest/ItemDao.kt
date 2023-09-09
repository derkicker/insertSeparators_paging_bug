package com.example.pagingtest

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemDao {
    @Query("SELECT * FROM Item ORDER BY value ASC")
    fun getSource(): PagingSource<Int, Item>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(items: List<Item>)

    @Query("DELETE FROM Item")
    fun deleteAll()
}