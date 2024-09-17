import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination';

const PageSelector = ({ pagesCount, currentPage, onClick }) => {
  console.log(
    `Rendering PageSelector with pagesCount=${pagesCount}, currentPage=${currentPage}`
  );
  const paginationItems = [];

  // Add the first page
  paginationItems.push(
    <PaginationItem key={1}>
      <PaginationLink
        isActive={currentPage === 1}
        onClick={() => {
          if (1 != currentPage) {
            onClick(1);
          }
        }}
      >
        1
      </PaginationLink>
    </PaginationItem>
  );

  // Conditional ellipsis after the first page
  if (currentPage > 3) {
    paginationItems.push(<PaginationEllipsis key='ellipsis1' />);
  }

  // Calculate the range of pages to display around the current page
  const startPage = Math.max(currentPage - 1, 2); // Start from the second page
  const endPage = Math.min(currentPage + 1, pagesCount - 1); // Up to the second last page

  // Generate pagination items for the range
  for (let page = startPage; page <= endPage; page++) {
    paginationItems.push(
      <PaginationItem key={page}>
        <PaginationLink
          isActive={currentPage === page}
          onClick={() => {
            if (page != currentPage) {
              onClick(page);
            }
          }}
        >
          {page}
        </PaginationLink>
      </PaginationItem>
    );
  }

  // Conditional ellipsis before the last page
  if (currentPage < pagesCount - 2) {
    paginationItems.push(<PaginationEllipsis key='ellipsis2' />);
  }

  // Always add the last page if there is more than one page
  if (pagesCount > 1) {
    paginationItems.push(
      <PaginationItem key={pagesCount}>
        <PaginationLink
          isActive={currentPage === pagesCount}
          onClick={() => onClick(pagesCount)}
        >
          {pagesCount}
        </PaginationLink>
      </PaginationItem>
    );
  }

  return (
    <Pagination>
      <PaginationContent>
        <PaginationItem key='previous'>
          <PaginationPrevious
            disabled={currentPage === 1}
            onClick={() => {
              if (currentPage > 1) {
                onClick(currentPage - 1);
              }
            }}
          />
        </PaginationItem>
        {paginationItems}
        <PaginationItem key='next'>
          <PaginationNext
            disabled={currentPage === pagesCount}
            onClick={() => {
              if (currentPage < pagesCount) {
                onClick(currentPage + 1);
              }
            }}
          />
        </PaginationItem>
      </PaginationContent>
    </Pagination>
  );
};

export default PageSelector;
