package com.mulmeong.feed.read.api.infrastructure;

import com.mulmeong.feed.read.api.domain.document.Feed;
import com.mulmeong.feed.read.api.domain.document.QFeed;
import com.mulmeong.feed.read.api.domain.model.SortType;
import com.mulmeong.feed.read.api.domain.model.Visibility;
import com.mulmeong.feed.read.api.dto.in.FeedFilterRequestDto;
import com.mulmeong.feed.read.api.dto.out.FeedResponseDto;
import com.mulmeong.feed.read.common.utils.CursorPage;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.support.SpringDataMongodbQuery;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class FeedCustomRepository {     // QueryDSL Repository

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE_NUMBER = 0;

    private final MongoTemplate mongoTemplate;
    private final QFeed feed = QFeed.feed;

    public CursorPage<FeedResponseDto> getFeedsByCategoryOrTag(FeedFilterRequestDto requestDto) {

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(feed.visibility.eq(Visibility.VISIBLE));

        Optional.ofNullable(requestDto.getCategoryName()).ifPresent(category ->
            builder.and(feed.categoryName.eq(category)));
        Optional.ofNullable(requestDto.getHashtagName()).ifPresent(hashtag ->
            builder.and(feed.hashtags.any().name.eq(hashtag)));

        // 마지막 ID 커서 적용
        Optional.ofNullable(requestDto.getLastId()).ifPresent(id -> builder.and(feed.id.lt(id)));

        int curPageNo = Optional.ofNullable(requestDto.getPageNo()).orElse(DEFAULT_PAGE_NUMBER);
        int curPageSize = Optional.ofNullable(requestDto.getPageSize()).orElse(DEFAULT_PAGE_SIZE);
        int offset = Math.max(0, (curPageNo - 1) * curPageSize);

        SpringDataMongodbQuery<Feed> query = new SpringDataMongodbQuery<>(mongoTemplate,
            Feed.class);

        List<Feed> content = query.where(builder)
            .orderBy(determineSortOrder(feed, requestDto.getSortType()))
            .offset(offset)
            .limit(curPageSize + 1)
            .fetch();

        String nextCursor = null;
        boolean hasNext = false;

        if (content.size() > curPageSize) {
            hasNext = true;
            nextCursor = content.get(curPageSize).getId();  // 마지막 항목의 ID를 커서로 설정
            content = content.subList(0, curPageSize);      // 실제 페이지 사이즈 만큼 자르기
        }

        return new CursorPage<>(content.stream().map(FeedResponseDto::fromDocument).toList(),
            nextCursor, hasNext, content.size(), requestDto.getPageNo());
    }

    private OrderSpecifier<?> determineSortOrder(QFeed feed, SortType sortType) {

        return switch (sortType) {
            case LATEST -> feed.createdAt.desc();
            case LIKES -> feed.netLikes.desc();
        };
    }

}