package dev.pgm.roadmate.presentation.map

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.pgm.roadmate.domain.repository.MapSearchCoordinator

@Module
@InstallIn(SingletonComponent::class)
abstract class MapModule {

    @Binds
    abstract fun bindOfflineMapController(impl: OfflineMapManager): OfflineMapController

    @Binds
    abstract fun bindMapSearchCoordinator(impl: MapSearchCoordinatorImpl): MapSearchCoordinator
}
