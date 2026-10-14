# Все эндпоинты Retrofit из APK

Сгенерировано `scripts/list_endpoints.py` из декомпилированного `RutubeTv 31.18.0`. Аннотации `retrofit2.http` в APK обфусцированы (`jo.a…jo.y`), скрипт переводит их обратно.
Базовый хост у разных интерфейсов свой: в основном `https://rutube.ru/`, платёжные `v1/…`, `v2/…`, `v5/…` идут на отдельный сервис. «(dynamic @Url)» — адрес приходит от сервера (ленты, плейлисты, реклама).
Проверенные вживую эндпоинты и их смысл — в [api.md](api.md).

| Path | Method | Params | Flags | Returns | Interface |
|---|---|---|---|---|---|
| (dynamic @Url) | DELETE | url, query:video_id | Authorization Headers{"Content-Type: application/json"} | C14393a0 | mb0.a |
| (dynamic @Url) | GET | url |  | AbstractC8287x0 | bi0.a |
| (dynamic @Url) | GET | url |  | PlaylistPaginationPageResponse | ud0.a |
| (dynamic @Url) | GET | url |  | RecomsResponse | ru.rutube.tv.feature.recommendations.a |
| (dynamic @Url) | GET | url |  | SerialContentPageResponse | me0.a |
| (dynamic @Url) | GET | url | Authorization | FavouritesPageResponse | ib0.a |
| (dynamic @Url) | GET | url | Authorization | SubscriptionsPageResponse | mb0.a |
| (dynamic @Url) | GET | url, headermap |  | C14364z0<BannerDTO> | BannerApi |
| (dynamic @Url) | GET | url, headermap |  | C14364z0<VastXML> | VastApi |
| (dynamic @Url) | GET | url, headermap, querymap |  | C14364z0<BannerRulesDTO> | BannerRulesApi |
| (dynamic @Url) | GET | url, querymap |  | C14393a0 | y70.a |
| (dynamic @Url) | GET | url, querymap |  | UserCommunicationResponse | ru.rutube.communicationpopup.base.data.a |
| (dynamic @Url) | PATCH | url, body | Authorization Headers{"Content-Type: application/json"} | SubscriptionUpdatedResponse | mb0.a |
| (dynamic @Url) | POST | url, body, query:video_id | Authorization Headers{"Content-Type: application/json"} | SubscriptionResponse | mb0.a |
| /reactions | POST | body | Authorization Headers{"Content-Type:application/json"} | C14393a0 | he0.b |
| /serial/{videoId} | GET | path:videoId |  | SerialContentInfoResponse | me0.a |
| /v1/payment-methods/ | DELETE | query:paymentMethodId |  | C14364z0<C14393a0> | gc0.a |
| /v1/payment-methods/user/saved | GET |  |  | PaymentMethodsResponse | gc0.a |
| api/acl/blockingrule/{ruleId} | GET | path:ruleId |  | AclBlockingRuleResponse | si0.a |
| api/common_subscription/list | GET |  |  | CommonSubscriptionResponse | vb0.a |
| api/common_subscription/{channel_id} | GET | path:channel_id |  | CommonSubscriptionResponse | vb0.a |
| api/forms/complain | POST | body |  | NetworkResponse<C14393a0, C14393a0> | v80.a |
| api/forms/feedback | POST | body |  | NetworkResponse<C14393a0, C14393a0> | v80.a |
| api/health/live | HEAD |  |  | C14393a0 | tb0.a |
| api/info/pages/{path}/ | GET | path:path |  | UserAgreementResponse | ru.rutube.rupassauth.screen.terms.tv.data.a |
| api/metainfo/playlist/favorite/{id} | DELETE | path:id | Authorization | C14393a0 | ru.rutube.multiplatform.shared.favoriteplaylists.data.a |
| api/metainfo/playlist/favorite/{id} | GET | path:id | Authorization | FavoritePlaylistResponse | ru.rutube.multiplatform.shared.favoriteplaylists.data.a |
| api/metainfo/playlist/favorite/{id} | POST | path:id | Authorization | C14393a0 | ru.rutube.multiplatform.shared.favoriteplaylists.data.a |
| api/numerator/video/{videoId}/vote | GET | path:videoId |  | GetReactionsCountResponse | he0.b |
| api/playlist/custom/{playlistId}/ | GET | path:playlistId |  | PlaylistInfoResponse | ru.rutube.rutubecore.manager.playlist.api.g |
| api/playlist/custom/{playlistId}/ | GET | path:playlistId |  | PlaylistInfoResponse | ud0.a |
| api/playlist/future/ | GET | query:page, query:limit | Authorization | PlaylistInfo | ru.rutube.rutubecore.manager.playlist.api.g |
| api/playlist/future/video/{PATH_VIDEO_ID}/ | DELETE | path:PATH_VIDEO_ID | Authorization Headers{"Content-Type: application/json"} | C14393a0 | ib0.a |
| api/playlist/future/video/{PATH_VIDEO_ID}/ | POST | path:PATH_VIDEO_ID | Authorization Headers{"Content-Type: application/json"} | C14393a0 | ib0.a |
| api/premier/app/v1/onboardings/count/{target} | POST | path:target, body | Headers{"Content-Type:application/json"} | UserCommunicationResponse | ru.rutube.communicationpopup.base.data.a |
| api/profile/user/{userId} | GET | path:userId |  | RtProfileResponse | ru.rutube.rutubecore.manager.playlist.api.g |
| api/search/autocomplete/video/ | GET | query:query |  | SearchAutocompleteResponse | ru.rutube.mutliplatform.shared.search.suggestions.c |
| api/search/history/delete_all | POST |  |  | C14393a0 | ru.rutube.mutliplatform.shared.search.history.datasource.remote.f |
| api/v1/channels/{id}/pinned_playlists | GET | path:id, query:limit, query:limit_video, query:page, query:order_by |  | PinnedPlaylistsResponse | ru.rutube.multiplatform.shared.channel.pinnedplaylists.data.a |
| api/v2/reactions/videos/{videoId} | GET | path:videoId | Authorization | GetUserReactionResponse | he0.b |
| api/v2/search/history/ | GET |  |  | SearchHistoryResponse | ru.rutube.mutliplatform.shared.search.history.datasource.remote.f |
| api/v2/video/end-screen/{video_id}/?limit=0 | GET | path:video_id |  | EndScreenNextVideoResponse | xj0.a |
| api/video/history | DELETE |  |  | C14393a0 | ru.rutube.app.network.source.a |
| api/video/history/actual/ | GET |  | Authorization | VideoHistoryResponse | de0.a |
| api/video/position/{video_id} | GET | path:video_id, query:t |  | VideoProgressResponse | de0.a |
| api/video/recommendation/{VIDEO_ID_PATH}/ | GET | path:VIDEO_ID_PATH, querymap |  | List<RecommendationResponse> | mj0.a |
| api/video/stream/{videoId}/info/ | GET | path:videoId |  | UserStreamInfoResponse | si0.a |
| api/video/{videoId}/thumbnail | GET | path:videoId |  | VideoThumbnailResponse | si0.a |
| api/v{apiVer}/notifications/settings/ | PATCH | path:apiVer, body |  | C14364z0<C14393a0> | il0.a |
| config | GET | query:solution, query:version |  |  | OnlineConfigDataSource |
| features/advert-disable | GET | query:video_id, query:author_id |  | AdvertDisableResponse | ru.rutube.multiplatform.shared.advertdisable.data.a |
| multipass/api/v3/accounts/phone/confirm | POST | body |  | C14393a0 | il0.a |
| multipass/api/v3/accounts/phone/set | POST | body |  | C8516a | il0.a |
| multipass/api/v3/accounts/timestamp | GET |  |  | TimestampResponse | sa0.a |
| multipass/api/v{apiVer}/accounts/email/login | POST | path:apiVer, body |  | C7220e | il0.a |
| multipass/api/v{apiVer}/accounts/email/password | GET | path:apiVer, query:email, query:captcha |  | C11233l | il0.a |
| multipass/api/v{apiVer}/accounts/email/register | POST | path:apiVer, body |  | C8516a | il0.a |
| multipass/api/v{apiVer}/accounts/email/send_otp | POST | path:apiVer, body |  | C8516a | il0.a |
| multipass/api/v{apiVer}/accounts/password | POST | path:apiVer, body |  | AbstractC8287x0 | il0.a |
| multipass/api/v{apiVer}/accounts/phone/login | POST | path:apiVer, body |  | C7220e | il0.a |
| multipass/api/v{apiVer}/accounts/phone/password | GET | path:apiVer, query:phone, query:captcha |  | C11233l | il0.a |
| multipass/api/v{apiVer}/accounts/phone/register | POST | path:apiVer, body |  | C8516a | il0.a |
| multipass/api/v{apiVer}/accounts/phone/send_otp | POST | path:apiVer, body |  | C8516a | il0.a |
| multipass/api/v{apiVer}/oauth2/device/code | POST | path:apiVer, body |  | C15297b | il0.a |
| multipass/api/v{apiVer}/oauth2/device/token | POST | path:apiVer, body |  | C15298c | il0.a |
| navigation/Android_TV | GET | header:x-client-version |  | NavigationResponse | nq0.a |
| v1/payment/async | POST | header:idempotency-key, body | Headers{"Content-Type:application/json"} | PaymentAsyncResponse | bc0.a |
| v1/payment/recurrent-recovery | PATCH | body | Headers{"Content-Type:application/json"} | C14393a0 | vb0.a |
| v1/payment/{id}/status | GET | path:id |  | PayStatusResponse | bc0.a |
| v1/promo-codes/activation-status | GET | query:promocode, query:productCode |  | PromocodeActivationStatusResponse | vc0.a |
| v1/subscriptions/offer/{productCode} | GET | path:productCode |  | FreezeSubscriptionResponse | vb0.a |
| v2/promo-codes/activation | POST | body | Headers{"Content-Type:application/json"} | PromocodeActivationResponse | vc0.a |
| v3/promo-codes/{promocode} | GET | path:promocode |  | PromocodeResponse | vc0.a |
| v5/product-tariff | GET | query:productCode |  | PaidSubscriptionsResponseV5 | vb0.a |
| video/{id}/annotation | GET | path:id, query:featureUseCount, query:author_id |  | SkipIntroAndTitlesResponse | ru.rutube.multiplatform.shared.skipintroandtitles.m |
| video/{videoId} | GET | path:videoId, query:pepper |  | PlayInfo | ru.rutube.player.playinfoprovider.internal.a |
