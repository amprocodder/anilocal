package com.anilocal.app.data.repo

// Repository implementations now live next to their data sources:
//  - CatalogRepository  -> data/metadata/anilist/AniListCatalogRepository
//  - StreamRepository   -> data/source/SourceStreamRepository
//  - SkipRepository     -> data/skip/AniSkipRepository
//  - LibraryRepository  -> data/local/LocalRepositories (LibraryRepositoryImpl)
//  - ProgressRepository -> data/local/LocalRepositories (ProgressRepositoryImpl)
// Every binding is wired in data/DataGraph.kt. This file is intentionally left as a pointer.
