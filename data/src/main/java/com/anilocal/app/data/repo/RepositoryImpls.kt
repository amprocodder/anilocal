package com.anilocal.app.data.repo

// Repository implementations now live next to their data sources:
//  - CatalogRepository  -> data/metadata/anilist/AniListCatalogRepository
//  - StreamRepository   -> data/source/SourceStreamRepository
//  - SkipRepository     -> data/skip/AniSkipRepository
//  - LibraryRepository  -> data/local/RoomLibraryRepository
//  - ProgressRepository -> data/local/RoomProgressRepository
//  - AuthRepository     -> data/auth/FirebaseAuthRepository
// Bindings are wired in di/AppModule.kt. This file is intentionally left as a pointer.
