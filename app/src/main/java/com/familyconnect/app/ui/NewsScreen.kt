package com.familyconnect.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class NewsArticle(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val publishedAt: String,
    val url: String,
    val imageUrl: String? = null,
    val category: NewsCategory
)

enum class NewsCategory(val displayName: String) {
    GEOPOLITICS("🌍 Geopolitics"),
    STOCK_MARKET("📈 Indian Stock Market"),
    AI("🤖 AI News"),
    WORLD_FINANCIAL("🌐 World Financial"),
    INDIA_FINANCIAL("🇮🇳 India Financial"),
    WORLD_STOCK_MARKET("📊 World Stock Market")
}

// Mock news data - Replace with real API calls
fun getMockNewsArticles(): List<NewsArticle> {
    return listOf(
        // Geopolitics
        NewsArticle(
            id = "geo1",
            title = "India-China Border Tensions Ease with New Trade Agreement",
            description = "Recent diplomatic talks between India and China have led to a preliminary trade agreement aimed at reducing border tensions.",
            source = "Reuters",
            publishedAt = "2 hours ago",
            url = "https://example.com/geo1",
            category = NewsCategory.GEOPOLITICS
        ),
        NewsArticle(
            id = "geo2",
            title = "BRICS Summit: India Proposes New Payment System",
            description = "India's proposal for an alternative international payment system gains traction among BRICS members.",
            source = "BBC",
            publishedAt = "4 hours ago",
            url = "https://example.com/geo2",
            category = NewsCategory.GEOPOLITICS
        ),
        NewsArticle(
            id = "geo3",
            title = "Middle East Stabilization Efforts Show Promise",
            description = "UN-backed initiatives in the Middle East region show signs of reducing regional conflicts.",
            source = "Al Jazeera",
            publishedAt = "6 hours ago",
            url = "https://example.com/geo3",
            category = NewsCategory.GEOPOLITICS
        ),

        // Stock Market
        NewsArticle(
            id = "stock1",
            title = "Sensex Reaches All-Time High Amid Recovery",
            description = "The Bombay Stock Exchange's Sensex index hits a new record as tech and banking stocks surge.",
            source = "Economic Times",
            publishedAt = "1 hour ago",
            url = "https://example.com/stock1",
            category = NewsCategory.STOCK_MARKET
        ),
        NewsArticle(
            id = "stock2",
            title = "RBI Maintains Benchmark Rate at 6.5%",
            description = "Reserve Bank of India keeps the repo rate unchanged at 6.5%, supporting market stability.",
            source = "Moneycontrol",
            publishedAt = "3 hours ago",
            url = "https://example.com/stock2",
            category = NewsCategory.STOCK_MARKET
        ),
        NewsArticle(
            id = "stock3",
            title = "IT Stocks Rally on Strong Q4 Earnings",
            description = "Major IT companies report better-than-expected earnings, driving strong market performance.",
            source = "BSE India",
            publishedAt = "5 hours ago",
            url = "https://example.com/stock3",
            category = NewsCategory.STOCK_MARKET
        ),

        // AI
        NewsArticle(
            id = "ai1",
            title = "India Launches National AI Research Initiative",
            description = "Government announces major funding for AI research with focus on healthcare and agriculture applications.",
            source = "TechCrunch India",
            publishedAt = "30 minutes ago",
            url = "https://example.com/ai1",
            category = NewsCategory.AI
        ),
        NewsArticle(
            id = "ai2",
            title = "New AI Model Achieves Breakthrough in Language Understanding",
            description = "Researchers develop an AI model with unprecedented capabilities in natural language processing.",
            source = "Nature AI",
            publishedAt = "2 hours ago",
            url = "https://example.com/ai2",
            category = NewsCategory.AI
        ),
        NewsArticle(
            id = "ai3",
            title = "AI Ethics Guidelines Released by International Coalition",
            description = "Leading tech companies and governments collaborate to establish global AI ethics standards.",
            source = "MIT Technology Review",
            publishedAt = "4 hours ago",
            url = "https://example.com/ai3",
            category = NewsCategory.AI
        ),

        // World Financial
        NewsArticle(
            id = "wf1",
            title = "Global Banking Sector Shows Strong Recovery Post-Crisis",
            description = "International financial institutions report record profits as economic conditions stabilize globally.",
            source = "Reuters Finance",
            publishedAt = "1 hour ago",
            url = "https://example.com/wf1",
            category = NewsCategory.WORLD_FINANCIAL
        ),
        NewsArticle(
            id = "wf2",
            title = "European Central Bank Signals Potential Rate Cuts",
            description = "ECB hints at possible interest rate reductions to support economic growth across the Eurozone.",
            source = "Bloomberg",
            publishedAt = "3 hours ago",
            url = "https://example.com/wf2",
            category = NewsCategory.WORLD_FINANCIAL
        ),
        NewsArticle(
            id = "wf3",
            title = "US Treasury Bonds See Renewed Investor Interest",
            description = "Strong demand for US government bonds reflects investor confidence in American economic recovery.",
            source = "Wall Street Journal",
            publishedAt = "5 hours ago",
            url = "https://example.com/wf3",
            category = NewsCategory.WORLD_FINANCIAL
        ),
        NewsArticle(
            id = "wf4",
            title = "Asian Markets Show Growth Despite Trade Tensions",
            description = "Financial markets across Asia demonstrate resilience with positive quarterly performance indicators.",
            source = "Financial Times",
            publishedAt = "2 hours ago",
            url = "https://example.com/wf4",
            category = NewsCategory.WORLD_FINANCIAL
        ),

        // India Financial
        NewsArticle(
            id = "if1",
            title = "India's GDP Growth Accelerates to 7.2% in Q4",
            description = "Indian economy shows robust growth driven by strong domestic demand and manufacturing sector expansion.",
            source = "Economic Times",
            publishedAt = "30 minutes ago",
            url = "https://example.com/if1",
            category = NewsCategory.INDIA_FINANCIAL
        ),
        NewsArticle(
            id = "if2",
            title = "Indian Rupee Strengthens Against Major Currencies",
            description = "The rupee gains value as foreign investors increase equity allocations to Indian markets.",
            source = "Moneycontrol",
            publishedAt = "1 hour ago",
            url = "https://example.com/if2",
            category = NewsCategory.INDIA_FINANCIAL
        ),
        NewsArticle(
            id = "if3",
            title = "India's FDI Inflows Hit Record High at $85 Billion",
            description = "Foreign Direct Investment into India reaches unprecedented levels, boosting manufacturing and tech sectors.",
            source = "Business Standard",
            publishedAt = "4 hours ago",
            url = "https://example.com/if3",
            category = NewsCategory.INDIA_FINANCIAL
        ),
        NewsArticle(
            id = "if4",
            title = "Indian Banks Report Strong Quarterly Earnings",
            description = "Major Indian banking institutions post record profits with improving asset quality and lower NPAs.",
            source = "Financial Express",
            publishedAt = "3 hours ago",
            url = "https://example.com/if4",
            category = NewsCategory.INDIA_FINANCIAL
        ),
        NewsArticle(
            id = "if5",
            title = "Insurance Sector Boom: India Leads Global Growth",
            description = "India's insurance market grows at fastest rate globally with digital adoption and increased coverage.",
            source = "The Hindu BusinessLine",
            publishedAt = "2 hours ago",
            url = "https://example.com/if5",
            category = NewsCategory.INDIA_FINANCIAL
        ),

        // World Stock Market
        NewsArticle(
            id = "wsm1",
            title = "Tech Stocks Rally on Strong AI Company Earnings",
            description = "Major technology companies report better-than-expected earnings driving significant market gains.",
            source = "CNBC",
            publishedAt = "45 minutes ago",
            url = "https://example.com/wsm1",
            category = NewsCategory.WORLD_STOCK_MARKET
        ),
        NewsArticle(
            id = "wsm2",
            title = "S&P 500 Reaches New All-Time High",
            description = "US stock market benchmark hits historic levels as investors gain confidence in economic recovery.",
            source = "Reuters",
            publishedAt = "2 hours ago",
            url = "https://example.com/wsm2",
            category = NewsCategory.WORLD_STOCK_MARKET
        ),
        NewsArticle(
            id = "wsm3",
            title = "European Stocks Gain on Rate Relief Expectations",
            description = "European stock indices advance as market anticipates potential interest rate cuts by central banks.",
            source = "MarketWatch",
            publishedAt = "3 hours ago",
            url = "https://example.com/wsm3",
            category = NewsCategory.WORLD_STOCK_MARKET
        ),
        NewsArticle(
            id = "wsm4",
            title = "Energy Stocks Surge on Commodity Price Recovery",
            description = "Oil and gas companies see significant stock price increases as crude prices stabilize at higher levels.",
            source = "Yahoo Finance",
            publishedAt = "4 hours ago",
            url = "https://example.com/wsm4",
            category = NewsCategory.WORLD_STOCK_MARKET
        ),
        NewsArticle(
            id = "wsm5",
            title = "Emerging Markets Show Outperformance vs Developed Markets",
            description = "Stocks in emerging markets including India, Brazil, and Vietnam outpace developed market gains.",
            source = "Bloomberg",
            publishedAt = "5 hours ago",
            url = "https://example.com/wsm5",
            category = NewsCategory.WORLD_STOCK_MARKET
        )
    )
}

@Composable
fun NewsScreen() {
    var selectedCategory by remember { mutableStateOf<NewsCategory?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var newsArticles by remember { mutableStateOf(getMockNewsArticles()) }

    // Handle refresh with LaunchedEffect
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1500)
            newsArticles = getMockNewsArticles().shuffled()
            isRefreshing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Header with refresh button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("📰 News Feed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Latest from around the world", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            IconButton(
                onClick = { isRefreshing = true },
                modifier = Modifier.background(
                    color = Color(0xFF5C6BC0),
                    shape = RoundedCornerShape(8.dp)
                ),
                enabled = !isRefreshing
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Category tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // All category
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All News", fontSize = 12.sp) },
                modifier = Modifier.height(36.dp)
            )
            // Individual categories
            NewsCategory.values().forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category.displayName, fontSize = 12.sp) },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        // Loading indicator
        if (isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Refreshing news...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            // News list
            val filteredArticles = if (selectedCategory == null) {
                newsArticles
            } else {
                newsArticles.filter { it.category == selectedCategory }
            }

            if (filteredArticles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭 No news available", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Try refreshing or selecting a different category", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredArticles) { article ->
                        NewsArticleCard(article)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsArticleCard(article: NewsArticle) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(article.url) }
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Category badge
            Surface(
                modifier = Modifier.wrapContentSize(),
                shape = RoundedCornerShape(4.dp),
                color = when (article.category) {
                    NewsCategory.GEOPOLITICS -> Color(0xFFFFE0B2)
                    NewsCategory.STOCK_MARKET -> Color(0xFFC8E6C9)
                    NewsCategory.AI -> Color(0xFFBBDEFB)
                    NewsCategory.WORLD_FINANCIAL -> Color(0xFFFFCDD2)
                    NewsCategory.INDIA_FINANCIAL -> Color(0xFFD1C4E9)
                    NewsCategory.WORLD_STOCK_MARKET -> Color(0xFFC8E6C9)
                }
            ) {
                Text(
                    article.category.displayName,
                    modifier = Modifier.padding(6.dp, 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Title
            Text(
                article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            Text(
                article.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.Gray
            )

            // Footer: Source and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    article.source,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5C6BC0)
                )
                Text(
                    article.publishedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}
